/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import config._

/** 通用化的 ALU 編碼結構。通過設計 ALU 指令，將ISA與架構實現分離。
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @param `[6]`
  *   Invert bit. 描述逻辑翻转位. 当这一位拉高时, 表示对不拉高的逻辑的翻转.
  *
  * @param `[5]`
  *   Word bit. XLEN == 64 時, 當這一位拉高時，指 Word 數據類型. XLEN == 32時, 這一位被拋棄.
  *
  * @param `[4]`
  *   Unsigned bit. 無符號標誌位. 當這一位拉高時, 對 可拓展原數據 進行0拓展, 否則將會使用符號拓展
  *   - 对于 Encoded type 为 0 的类型而言, 代表u类型
  *   - 对于 Encoded type 为 1 的类型而言, 代表逻辑移动
  *
  * @param `[2,0]`
  *   Encoded type bit. 編碼後類型, 用於在無法通過其他位分別時, 區分不同指令. 當編碼全爲 0 時用於區分不需要進行匹配
  */
object ALUCtrl {
  def add = "b000_0_000".U
  def addu = "b001_0_000".U
  def sub = "b100_0_000".U
  def addw = "b010_0_000".U
  def addwu = "b011_0_000".U
  def subw = "b110_0_000".U

  // Same encoded type, but diffrent word bit.
  // Invert bit not work acutally.
  def sll = "b001_0_001".U
  def srl = "b101_0_001".U
  def sra = "b100_0_001".U
  def sllw = "b011_0_001".U
  def srlw = "b111_0_001".U
  def sraw = "b110_0_001".U

  def slt = "b100_0_010".U // 需要比較
  def sltu = "b101_0_010".U // 需要比較

  def or = "b000_0_011".U
  def nor = "b000_0_100".U
  def xor = "b000_0_101".U
  def and = "b000_0_110".U

  /** 用於判斷是否是 Word 類型. 在32位的情況下沒有意義
    *
    * @param ctrl
    * @return
    */
  def isWord(ctrl: UInt) = ctrl(5)

  /** 判斷是否是無符號數
    *
    * @param ctrl
    * @return
    */
  def isUnsign(ctrl: UInt) = ctrl(4)

  /** 判断当前指令是否需要进行逻辑翻转.
    *
    * Rule:
    *   - right is the `invert` of left
    *   - sub is the `invert` of add
    *   - not(nor, nand) is the `invert` of is(or, and)
    *
    * @param ctrl
    * @return
    */
  def isInvert(ctrl: UInt) = ctrl(6)

  /** 訪問編碼位
    *
    * @param ctrl
    * @return
    */
  def getEncoded(ctrl: UInt) = ctrl(2, 0)
}

class ALUIO extends FuCtrlIO {}

class ALU extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new ALUIO)

  val (valid, srcA, srcB, ctrl) =
    (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
  def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
    this.valid := valid
    this.srcA := srcA
    this.srcB := srcB
    this.ctrl := ctrl
    io.out.bits
  }

  // ==== Caculate Logic ====
  // === Rename Ctrl Sig ===
  val isInvert = ALUCtrl.isInvert(ctrl)
  val isWord = ALUCtrl.isWord(ctrl)
  val isUnsign = ALUCtrl.isUnsign(ctrl)

  // === Addr gen ===
  val isAddrSub = isInvert
  val (adderRes, adderCarry) =
    AdderGen(XLEN, srcA, (srcB ^ Fill(XLEN, isAddrSub)), isAddrSub)

  // === Logic ===
  val xorRes = srcA ^ srcB
  val andRes = srcA & srcB
  val orRes = srcA | srcB
  val norRes = ~orRes

  // === Cmp ====
  val sltu = !adderCarry
  val slt = xorRes(XLEN - 1) ^ sltu

  // === Shift ===
  val shsrc = Mux(
    isUnsign,
    ZeroExt(srcA(31, 0), XLEN),
    SignExt(srcA(31, 0), XLEN)
  )
  val shamt = Mux(
    isWord,
    srcB(4, 0),
    BaseConfig.isa match {
      case ISA.MIPS => srcB(4, 0)
      case _        => if (XLEN == 64) srcB(5, 0) else srcB(4, 0)
    }
  )
  val shout = Mux(
    isUnsign,
    Mux(
      isInvert,
      shsrc >> shamt,
      (shsrc << shamt)(XLEN - 1, 0)
    ),
    (shsrc.asSInt >> shamt).asUInt
  )

  // ==== Choose Logic ====
  val res = MuxLookup(ALUCtrl.getEncoded(ctrl), adderRes)(
    Seq(
      ALUCtrl.getEncoded(ALUCtrl.sll) -> shout,
      ALUCtrl.getEncoded(ALUCtrl.slt) -> ZeroExt(
        Mux(isUnsign, sltu, slt),
        XLEN
      ), // 對 Bool 值進行0拓展
      ALUCtrl.getEncoded(ALUCtrl.or) -> orRes,
      ALUCtrl.getEncoded(ALUCtrl.and) -> andRes,
      ALUCtrl.getEncoded(ALUCtrl.nor) -> norRes,
      ALUCtrl.getEncoded(ALUCtrl.xor) -> xorRes
    )
  )

  io.out.bits := res
  io.in.ready := io.out.ready
  io.out.valid := valid

  // ==== Log ====
  if (BaseConfig.get("LogALU")) {
    Debug(
      io.in.fire,
      "[In  Fire] Ctrl %b SrcA 0x%x, SrcB 0x%x\n",
      ctrl,
      srcA,
      srcB
    )
    Debug(
      io.out.fire,
      "[Out Fire] Out 0x%x\n",
      io.out.bits
    )
  }
}
