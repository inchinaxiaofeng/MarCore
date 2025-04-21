package module.fu.riscv

import module.fu._
import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import module.fu.BPUUpdate
import isa.riscv._
import top.Settings
import blackbox._

/** 通用化的 ALU 編碼結構。通過設計 ALU 指令，將ISA與架構實現分離。
  *
  * ALU Ctrl 的位寬將會影響流水間寄存器的開銷, 因此要節約位寬,採取了編碼的方式進行.
  *
  * 但是爲了避免編碼解碼開銷, 在可以的情況下, 儘量賦予每一個位獨特的意義.
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @param `[6]`
  *   Add bit. 可以通過這一位判斷當前是加還是減
  *
  * @param `[5]`
  *   Word bit. XLEN == 64 時, 當這一位爲1時，指 Word 數據類型. XLEN == 32時, 這一位被拋棄.
  *
  * @param `[4]`
  *   Branch Unit Bit. 當這一位爲1時, 視作 Branch Unit 類型指令. 爲0時, 視作普通指令.
  *
  * @param `[3]([4]==1)`
  *   Branch inst bit. 0 代表分支指令(Branch); 1 代表跳轉指令(Jump).
  *
  * @param `[2:0]([4]==1,[3]==0)`
  *   `[2:1]`: Branch type bit. `[0]`: Branch direction invert bit.
  *
  * @param `[2:0]([4]==1,[3]==1)`
  *   Type bit. 特別的有: 當 `[2:1]` 爲`b11`時, 代表函數調用與返回指令. 末位爲0爲調用,
  *   1爲返回(可以視作調用的invert)
  *
  * @param `[3:0]([4]==0)`
  *   Type bit.
  */
object ALUCtrl {
  def add = "b100_0000".U
  def sll = "b000_0001".U
  def slt = "b000_0010".U
  def sltu = "b00_00011".U
  def xor = "b000_0100".U
  def srl = "b000_0101".U
  def or = "b000_0110".U
  def and = "b000_0111".U
  def sub = "b000_1000".U
  def sra = "b000_1101".U

  def addw = "b1100000".U
  def subw = "b0101000".U
  def sllw = "b0100001".U
  def srlw = "b0100101".U
  def sraw = "b0101101".U

  // For RAS
  def call = "b1011_110".U // 函數調用指令
  def ret = "b1011_111".U // 函數返回指令

  def jal = "b1011_000".U
  def jalr = "b1011_010".U

  def beq = "b0010_000".U // Branch if equal, ==
  def bne = "b0010_001".U // Branch if not equal, !=
  def blt = "b0010_100".U // Branch if less then, <
  def bge = "b0010_101".U // Branch if greater equal, >=
  def bltu = "b0010_110".U // Branch if less then (unsigned)
  def bgeu = "b0010_111".U // Branch if greater equal (unsigned)

  def isAdd(ctrl: UInt) = ctrl(6)
  def isWordOp(ctrl: UInt) = ctrl(5)
  def isBru(ctrl: UInt) = ctrl(4)
  def isBranch(ctrl: UInt) = !ctrl(3)
  def getBranchType(ctrl: UInt) = ctrl(2, 1)
  def isBranchInvert(ctrl: UInt) = ctrl(0)
  def isJump(ctrl: UInt) = ctrl(3)
}

class ALUIO extends FuCtrlIO {
  val cfIn = Flipped(new CtrlFlowIO)
  val redirect = new RedirectIO
  val offset = Input(UInt(XLEN.W))
  val bpuUpdate = new BPUUpdate
}

class RISCVALU extends MarCoreModule {
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

  val isAddrSub = !ALUCtrl.isAdd(ctrl)
  val (adderRes, adderCarry) =
    if (Settings.get("ImplBetterAdder")) {
      AdderGen(XLEN, srcA, (srcB ^ Fill(XLEN, isAddrSub)), isAddrSub)
    } else {
      val newAdderRes = ((srcA +& (srcB ^ Fill(XLEN, isAddrSub))) + isAddrSub)
      (newAdderRes(XLEN - 1, 0), newAdderRes(XLEN))
    }
  val xorRes = srcA ^ srcB
  val sltu = !adderCarry
  val slt = xorRes(XLEN - 1) ^ sltu

  val shsrcA = MuxLookup(ctrl, srcA(XLEN - 1, 0))(
    Seq(
      ALUCtrl.srlw -> ZeroExt(srcA(31, 0), XLEN),
      ALUCtrl.sraw -> SignExt(srcA(31, 0), XLEN)
    )
  )
  val shamt = Mux(
    ALUCtrl.isWordOp(ctrl),
    srcB(4, 0),
    if (XLEN == 64) srcB(5, 0) else srcB(4, 0)
  )
  val res = MuxLookup(ctrl(3, 0), adderRes)(
    Seq(
      ALUCtrl.sll -> ((shsrcA << shamt)(XLEN - 1, 0)),
      ALUCtrl.slt -> ZeroExt(slt, XLEN),
      ALUCtrl.sltu -> ZeroExt(sltu, XLEN),
      ALUCtrl.xor -> xorRes,
      ALUCtrl.srl -> (shsrcA >> shamt),
      ALUCtrl.or -> (srcA | srcB),
      ALUCtrl.and -> (srcA & srcB),
      ALUCtrl.sra -> ((shsrcA.asSInt >> shamt).asUInt)
    )
  )
  val aluRes = Mux(ALUCtrl.isWordOp(ctrl), SignExt(res(31, 0), 64), res)

  val branchOpTable = List(
    ALUCtrl.getBranchType(ALUCtrl.beq) -> !xorRes.orR,
    ALUCtrl.getBranchType(ALUCtrl.blt) -> slt,
    ALUCtrl.getBranchType(ALUCtrl.bltu) -> sltu
  )

  val jumpTarget =
    if (Settings.get("ImplBetterAdder"))
      AdderGen(XLEN, io.cfIn.pc, io.offset, 0.U)._1
    else (io.cfIn.pc + io.offset)(XLEN - 1, 0)

  val isBranch = ALUCtrl.isBranch(ctrl)
  val isBru = ALUCtrl.isBru(ctrl)
  val taken = LookupTree(ALUCtrl.getBranchType(ctrl), branchOpTable) ^ ALUCtrl
    .isBranchInvert(ctrl)
  val target = Mux(isBranch, jumpTarget, adderRes)(VAddrBits - 1, 0)
//	val predictWrong = Mux(!taken && isBranch, io.cfIn.brIdx(0), !io.cfIn.brIdx(0) || (io.redirect.target =/= io.cfIn.pnpc))
  val predictWrong = Mux(isBru, io.redirect.target =/= io.cfIn.pnpc, false.B)

  val starget =
    if (Settings.get("ImplBetterAdder")) AdderGen(XLEN, io.cfIn.pc, 4.U, 0.U)._1
    else (io.cfIn.pc + 4.U)(XLEN - 1, 0)

  if (Settings.get("TraceALU"))
    Debug(valid, "[ERROR] pc %x inst %x\n", io.cfIn.pc, io.cfIn.instr)
  io.redirect.target := Mux(!taken && isBranch, starget, target)
  // with branch predictor, this is actually to fix the wrong prediction
  io.redirect.valid := valid && isBru && predictWrong

  val stargetSign =
    if (Settings.get("ImplBetterAdder"))
      AdderGen(XLEN, SignExt(io.cfIn.pc, AddrBits), 4.U, 0.U)._1
    else (SignExt(io.cfIn.pc, AddrBits) + 4.U)(XLEN - 1, 0)
  val redirectRtype = if (EnableOutOfOrderExec) 1.U else 0.U
  io.redirect.rtype := redirectRtype
  // mark redirect type as speculative exec fix
  // may be can be moved to ISU to calculate pc + 4
  // this is actually for jal and jalr to write pc + 4/2 to rd
  io.out.bits := Mux(isBru, stargetSign, aluRes)

  io.in.ready := io.out.ready
  io.out.valid := valid

  io.bpuUpdate.valid := valid && isBru
  io.bpuUpdate.pc := io.cfIn.pc
  io.bpuUpdate.isMissPredict := predictWrong
  io.bpuUpdate.actualTarget := target
  io.bpuUpdate.actualTaken := taken
  io.bpuUpdate.fuCtrl := ctrl
  io.bpuUpdate.btbType := LookupTree(ctrl, RV32I_BRUInstr.bruCtrl2BtbTypeTable)

  if (Settings.get("Statistic")) {
    val statistic_bp = Module(new STATISTIC_BP)
    statistic_bp.io.clk := clock
    statistic_bp.io.rst := reset
    statistic_bp.io.predictValid := isBru
    statistic_bp.io.predictRight := !predictWrong
  }
  if (Settings.get("TraceALU")) {
    Debug(
      valid && isBru,
      "tgt %x valid %d npc %x pdwrong %x\n",
      io.redirect.target,
      io.redirect.valid,
      io.cfIn.pnpc,
      predictWrong
    )
    Debug(
      valid && isBru,
      "taken %d addrRes %x srcA %x srcB %x ctrl %x\n",
      taken,
      adderRes,
      srcA,
      srcB,
      ctrl
    )
    Debug(
      valid && isBru,
      "[BPW] pc %x tgt %x npc %x pdWrong %x type %x%x%x%x\n",
      io.cfIn.pc,
      io.redirect.target,
      io.cfIn.pnpc,
      predictWrong,
      isBranch,
      (ctrl === ALUCtrl.jal || ctrl === ALUCtrl.call),
      ctrl === ALUCtrl.jalr,
      ctrl === ALUCtrl.ret
    )
    Debug("valid %d isBru %d isBranch %d\n", valid, isBru, isBranch)
  }
}
