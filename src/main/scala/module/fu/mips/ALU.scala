package module.fu.mips

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import module.fu.BPUUpdate
import isa.mips._
import top.Settings

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
  def addu = "b1000001".U
  def sub = "b0001000".U
//	def subu	= "b0001001".U

  def sll = "b0000001".U
  def srl = "b0000101".U
  def sra = "b0001101".U
  def sllv = "b0000011".U
  def srlv = "b0000111".U
  def srav = "b0001111".U

  def slt = "b0000010".U
//	def sltu	= "b0000011".U

  def xor = "b0000100".U
  def or = "b0000110".U
  def and = "b0000111".U
//	def nor		= "b0001110".U

  // FIXME: Branch Delay Salt
  def j = "b0011000".U
  def jal = "b1011000".U
  def jalr = "b1011010".U
  def jr = "b0011010".U
  def beq = "b0010_00_0".U
  def bne = "b0010_00_1".U // NOTE: Invert of beq
  def bltz = "b0010_10_0".U
  def bgez = "b0010_10_1".U // NOTE: Invert of bltz
  def blez = "b0010_11_0".U
  def bgtz = "b0010_11_1".U // NOTE: Invert of blez

  def call = "b1011100".U
  def ret = "b1011110".U

  def isAdd(ctrl: UInt) = ctrl(6)
  def isWordOp(ctrl: UInt) = ctrl(5)
  def isBru(ctrl: UInt) = ctrl(4) // Just For Branch
  def isBranch(ctrl: UInt) = !ctrl(3) // Just For Branch
  def getBranchType(ctrl: UInt) = ctrl(2, 1)
  def isBranchInvert(ctrl: UInt) = ctrl(0)
  def isJump(ctrl: UInt) = isBru(ctrl) && !isBranch(ctrl)
}

class ALUIO extends FuCtrlIO {
  val cfIn = Flipped(new CtrlFlowIO_MIPS)
  val redirect = new RedirectIO
  val offset = Input(UInt(XLEN.W))
  val bpuUpdate = new BPUUpdate
}

class ALU extends MarCoreModule {
  println("This Fu Moulde is for [MIPS-C3 32 Bit]")
  require(XLEN == 32)
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
  val adderRes = (srcA +& (srcB ^ Fill(XLEN, isAddrSub))) + isAddrSub
  val xorRes = srcA ^ srcB
  val sltu = !adderRes(XLEN)
  val slt = xorRes(XLEN - 1) ^ sltu

  val shamt = srcB(
    4,
    0
  ) //	val shamt = Mux(ALUCtrl.isWordOp(ctrl), srcB(4, 0), if (XLEN == 64) srcB(5, 0) else srcB(4, 0))
  val res = MuxLookup(ctrl(3, 0), adderRes)(
    Seq(
      ALUCtrl.sll -> ((srcA << shamt)(XLEN - 1, 0)),
      ALUCtrl.srl -> (srcA >> shamt),
      ALUCtrl.sra -> ((srcA.asSInt >> shamt).asUInt),
      ALUCtrl.sllv -> ((srcA << io.offset(4, 0))(XLEN - 1, 0)),
      ALUCtrl.srlv -> (srcA >> io.offset(4, 0)),
      ALUCtrl.srav -> ((srcA.asSInt >> io.offset(4, 0)).asUInt),
      ALUCtrl.and -> (srcA & srcB),
      ALUCtrl.or -> (srcA | srcB),
      ALUCtrl.xor -> xorRes,
      ALUCtrl.slt -> ZeroExt(slt, XLEN)
    )
  )
  val aluRes =
    res //	val aluRes = Mux(ALUCtrl.isWordOp(ctrl), SignExt(res(31, 0), 64), res)

  val branchOpTable = List(
    ALUCtrl.getBranchType(ALUCtrl.beq) -> !xorRes.orR,
    ALUCtrl.getBranchType(ALUCtrl.bltz) -> slt,
    ALUCtrl.getBranchType(ALUCtrl.blez) -> (slt & !xorRes.orR)
  )

  val isBranch = ALUCtrl.isBranch(ctrl)
  val isBru = ALUCtrl.isBru(ctrl)
  val taken = LookupTree(ALUCtrl.getBranchType(ctrl), branchOpTable) ^ ALUCtrl
    .isBranchInvert(ctrl)
  val target = Mux(isBranch, /*jumpTarget*/ io.cfIn.pc + io.offset, adderRes)(
    VAddrBits - 1,
    0
  )
  val predictWrong = Mux(
    !taken && isBranch,
    io.cfIn.brIdx(0),
    !io.cfIn.brIdx(0) || (io.redirect.target =/= io.cfIn.pnpc)
  )
  io.redirect.target := Mux(
    !taken && isBranch,
    io.cfIn.pc + 8.U,
    target
  ) // FIXME: Branch delay salt
  // with branch predictor, this is actually to fix the wrong prediction
  io.redirect.valid := valid && isBru && predictWrong

  val redirectRtype = if (EnableOutOfOrderExec) 1.U else 0.U
  io.redirect.rtype := redirectRtype
  // FIXME: branch delay salt
  io.out.bits := Mux(isBru, SignExt(io.cfIn.pc, AddrBits) + 8.U, aluRes)

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

  io.in.ready := io.out.ready
  io.out.valid := valid

  io.bpuUpdate.valid := valid && isBru
  io.bpuUpdate.pc := io.cfIn.pc
  io.bpuUpdate.isMissPredict := predictWrong
  io.bpuUpdate.actualTarget := target
  io.bpuUpdate.actualTaken := taken
  io.bpuUpdate.fuCtrl := ctrl
  io.bpuUpdate.btbType := LookupTree(ctrl, MIPS32_BJInstr.bruCtrl2BtbTypeTable)
}
