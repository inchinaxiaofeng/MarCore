package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import isa.loongarch.LA32R_JumpInstr
import isa.mips.MIPS32_BJInstr
import isa.riscv.RV32I_BRUInstr
import defs._
import settings._
import config._
import utils._

/** 通用化 BRU 編碼結構
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @note
  *   我們不對是否寫回寄存器進行特殊編碼設計(也不應該這樣設計), 是因爲在CtrlFlowIO中已經通過一個獨熱信號指定了是否要寫回寄存器了.
  *
  * @param `[6]`
  *   Control transfer inst type. 轉移指令類型. `b0` Jump, `b1` Branch
  *
  * @param `[5]`
  *   Func call and ret. 函數調用返回類. 拉高代表當前跳轉被識別爲函數調用與返回. 這一位如果被指定1, 將會與RAS發生交互.
  *
  * @param `[4]`
  *   Indirect bit. 非直接目標標誌位. 拉高代表跳轉目標要基於Reg計算, 爲0代表跳轉目標是固定的(基於立即數或立即數與PC的計算)
  *
  * @param `[3]`
  *   Invert bit. 方向翻轉位. 當這位爲1時, 轉移方向翻轉.
  *
  * @param `[2]`
  *   Reserve
  *
  * @param `[1:0]`
  *   Encoded type bit. 編碼後類型, 用於在不能通過 1Hot 信號區分時, 標誌不同指令.
  */
object BRUCtrl {
  // For RAS
  def call = "b0110_000".U // 函數調用指令
  def ret = "b0111_000".U // 函數返回指令

  def j = "b0000_000".U // Jump 簡單跳轉, 不寫回(或寫回到x0)
  def jal = "b0000_001".U // Jump And Link 跳轉並保存返回地址
  def jr = "b0010_000".U // Jump Register 跳轉到寄存器指定地址, 不寫回
  def jalr = "b0010_001".U // Jump And Link Register 跳轉到 $rs 並保存返回地址到 $rd

  def beq = "b1000_000".U // Branch if equal, ==
  def bne = "b1001_000".U // Branch if not equal, !=
  def blt = "b1000_001".U // Branch if less then, <
  def ble = "b1000_010".U // Branch if less equal, <=
  def bge = "b1001_001".U // Branch if greater equal, >=
  def bgt = "b1001_010".U // Branch if greater then, >
  def bltu = "b1000_011".U // Branch if less then (unsigned)
  def bgeu = "b1001_011".U // Branch if greater equal (unsigned)

  /** 判斷轉移指令類型是否爲Branch
    *
    * @param ctrl
    * @return
    *   true表示當前類型爲Branch, false表示爲Jump
    */
  def isBranch(ctrl: UInt) = ctrl(6)

  /** 判斷轉移指令是否爲函數調用返回類.
    *
    * @param ctrl
    * @return
    *   true表示當前爲 Call 或者 Ret, false 表示爲其他類轉移指令
    */
  def isCallRet(ctrl: UInt) = ctrl(5)

  /** 判斷當前轉移指令的目標地址是否爲非固定(非直接獲得)
    *
    * @param ctrl
    * @return
    *   true 表示當前轉移指令目標地址需要通過運行時計算獲得, false 表示當前轉移指令目標地址是固定的 , 可以直接存儲與獲取
    */
  def isIndirect(ctrl: UInt) = ctrl(4)

  /** 判斷當前是否需要翻轉分支方向
    *
    * 翻轉位規定爲:
    *
    * Ret是Call的方向翻轉
    *
    * greater or equals 是 less then 的翻轉.
    *
    * @param ctrl
    * @return
    *   true代表爲翻轉類型, false表示不翻轉
    */
  def isInvert(ctrl: UInt) = ctrl(0)

  /** 獲得編碼後類型
    *
    * 需要通過訪問Get來判斷當前是否符合某一個Type
    *
    * @example
    *   {{{
    *    val res = MuxLookup(BRUCtrl.getEncoded(ctrl), 0.U)(
    *      Seq(
    *        BRUCtrl.getEncoded(beq) -> 0.U,
    *        BRUCtrl.getEncoded(bne) -> 1.U,
    *      )
    *    )
    *   }}}
    *
    * @param ctrl
    * @return
    *   將會返回編碼後的位數.
    */
  def getEncoded(ctrl: UInt) = ctrl(1, 0)
}

/** 基於FuCtrlIO拓展
  *
  * 對於 BRUIO 沒有任何使用規範. 每一個實現版本都應當自行規定, 並在前端驅動時自行符合.
  *
  * 公版驅動規範:
  *
  * @note
  *   對於 Branch 類指令, 以下情況將會執行跳轉: beq(srcA === srcB); beq(srcA =/= srcB);
  *   blt(srcA < srcB); bge(srcA >= srcB);
  *
  * @param io.in.valid
  *   拉高時, io.out.valid 將會被拉高, 輸出有效.
  *
  * @param io.in.bits.srcA
  *   等價於 RISCV 的 rs1, LoongArch 的 rj, MIPS 的 rs
  *
  * @param io.in.bits.srcB
  *   等價於 RISCV 的 rs2, LoongArch 的 rk, MIPS 的 rt
  *
  * @param io.in.bits.ctrl
  *
  * @param io.imm
  *   用於傳遞指令中的立即數
  */
class BRUIO extends FuCtrlIO {
  val cfIn = Flipped(new CtrlFlowIO)

  /** 用於在錯誤發生時進行重定向
    */
  val redirect = new RedirectIO

  /** 提供用於給PC計算的偏移值
    */
  val imm = Input(UInt(XLEN.W))
  val bpuUpdate = new BPUUpdate
}

/** 公版 BRU
  *
  * 由於 MIPS 需要分支延遲槽的原因, 寫回地址需要爲8.
  *
  * 評估後認爲在公版中提供支持會降低代碼可讀性, 因此在此不支持.
  */
class BRU extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new BRUIO)

  val (valid, srcA, srcB, ctrl) =
    (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
  def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
    this.valid := valid
    this.srcA := srcA
    this.srcB := srcB
    this.ctrl := ctrl
    io.out.bits
  }

  val isBranch = BRUCtrl.isBranch(ctrl)
  val isIndirect = BRUCtrl.isIndirect(ctrl)

  // 通過減法做比較, Branch類型時做減法
  val (adderRes, adderCarry) =
    AdderGen(XLEN, srcA, (srcB ^ Fill(XLEN, isBranch)), isBranch)

  // 靜態地址
  val starget = io.cfIn.pc + 4.U

  // 動態地址
  // 跳轉目標有兩個來源, 直接目標與間接目標
  val directTarget = (io.cfIn.pc + io.imm)(XLEN - 1, 0)

  val sltu = !adderCarry
  val xorRes = srcA ^ srcB
  val slt = xorRes(XLEN - 1) ^ sltu

  val branchOpTable = List(
    BRUCtrl.getEncoded(BRUCtrl.beq) -> !xorRes.orR,
    BRUCtrl.getEncoded(BRUCtrl.blt) -> slt,
    BRUCtrl.getEncoded(BRUCtrl.ble) -> (slt | ~xorRes.orR),
    BRUCtrl.getEncoded(BRUCtrl.bltu) -> sltu
  )

  // 分支方向
  val taken = LookupTree(BRUCtrl.getEncoded(ctrl), branchOpTable) ^ BRUCtrl
    .isInvert(ctrl)
  // 分支目標
  val target = Mux(isBranch, directTarget, adderRes)(VAddrBits - 1, 0)

  val predictWrong = io.redirect.target =/= io.cfIn.pnpc

  // 當實際計算出的地址與預測地址不一致時, 預測錯誤
  io.redirect.valid := valid && predictWrong
  val redirectRtype = if (EnableOutOfOrderExec) 1.U else 0.U
  io.redirect.rtype := redirectRtype
  io.redirect.target := Mux(!taken && isBranch, starget, target)

  io.bpuUpdate.valid := valid
  io.bpuUpdate.pc := io.cfIn.pc
  io.bpuUpdate.isMissPredict := predictWrong
  io.bpuUpdate.actualTarget := target
  io.bpuUpdate.actualTaken := taken
  io.bpuUpdate.fuCtrl := ctrl
  io.bpuUpdate.btbType := LookupTree(
    ctrl,
    BaseConfig.getT("ISA") match {
      case ISA.RISCV     => RV32I_BRUInstr.bruCtrl2BtbTypeTable
      case ISA.MIPS      => MIPS32_BJInstr.bruCtrl2BtbTypeTable
      case ISA.LoongArch => LA32R_JumpInstr.bruCtrl2BtbTypeTable
    }
  )

  val stargetSign =
    (SignExt(io.cfIn.pc, AddrBits) + BaseConfig.getT("ISA") match {
      case ISA.RISCV     => 4.U
      case ISA.MIPS      => 8.U
      case ISA.LoongArch => 4.U
    })(XLEN - 1, 0)
  // mark redirect type as speculative exec fix
  // may be can be moved to ISU to calculate pc + 4
  // this is actually for jal and jalr to write pc + 4/2 to rd
  io.out.bits := stargetSign
  io.in.ready := io.out.ready
  io.out.valid := valid

  if (Settings.get("TraceBRU")) {
    Debug(
      valid,
      "tgt %x valid %d npc %x pdwrong %x\n",
      io.redirect.target,
      io.redirect.valid,
      io.cfIn.pnpc,
      predictWrong
    )
    Debug(
      valid,
      "taken %d addrRes %x srcA %x srcB %x ctrl %x\n",
      taken,
      adderRes,
      srcA,
      srcB,
      ctrl
    )
    Debug(
      valid,
      "[BPW] pc %x tgt %x npc %x pdWrong %x type %x%x%x%x%x%x\n",
      io.cfIn.pc,
      io.redirect.target,
      io.cfIn.pnpc,
      predictWrong,
      isBranch,
      ctrl === BRUCtrl.j,
      ctrl === BRUCtrl.jal,
      ctrl === BRUCtrl.jr,
      ctrl === BRUCtrl.jalr,
      ctrl === BRUCtrl.call,
      ctrl === BRUCtrl.ret
    )
  }
}
