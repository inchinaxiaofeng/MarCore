package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import module.fu.BPUUpdate
import isa.riscv._
import top.Settings
import blackbox._

object ALUCtrl {
  /* [6]: Add bit.
   [5]: Word bit.
   [4]: Branch unit bit.
   [3]: Branch inst bit.
   [2:1]: Branch type bit.
   [0]: Branch direction invert bit.
   */
  //			= "b6543210".U
  def add = "b1000000".U
  def sll = "b0000001".U
  def slt = "b0000010".U
  def sltu = "b0000011".U
  def xor = "b0000100".U
  def srl = "b0000101".U
  def or = "b0000110".U
  def and = "b0000111".U
  def sub = "b0001000".U
  def sra = "b0001101".U

  def addw = "b1100000".U
  def subw = "b0101000".U
  def sllw = "b0100001".U
  def srlw = "b0100101".U
  def sraw = "b0101101".U

  def jal = "b1011000".U
  def jalr = "b1011010".U
  def beq = "b0010000".U
  def bne = "b0010001".U
  def blt = "b0010100".U
  def bge = "b0010101".U
  def bltu = "b0010110".U
  def bgeu = "b0010111".U

  // For RAS
  def call = "b1011100".U
  def ret = "b1011110".U

  def isAdd(ctrl: UInt) = ctrl(6)
  def pcPlus2(ctrl: UInt) = ctrl(5)
  def isWordOp(ctrl: UInt) = ctrl(5)
  def isBru(ctrl: UInt) = ctrl(4)
  def isBranch(ctrl: UInt) = !ctrl(3)
  def getBranchType(ctrl: UInt) = ctrl(2, 1)
  def isBranchInvert(ctrl: UInt) = ctrl(0)
  def isJump(ctrl: UInt) = isBru(ctrl) && !isBranch(ctrl)
}

class ALUIO extends FuCtrlIO {
  val cfIn = Flipped(new CtrlFlowIO)
  val redirect = new RedirectIO
  val offset = Input(UInt(XLEN.W))
  val bpuUpdate = new BPUUpdate
}

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
