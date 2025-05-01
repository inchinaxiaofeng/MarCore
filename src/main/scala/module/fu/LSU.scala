package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import module._

/** 通用化的 LSU 控制
  *
  * @param `[6]`
  *   Store bit. 拉高時代表Store操作.
  *   - 原子操作下, 拉高時代表Store-Conditional, 反之Load-Reserved
  *
  * @param `[5]`
  *   Atom Bit. 原子操作位. 拉高時代表Atom操作. 當拉高時, `[6]` 表示
  *
  * @param `[4]`
  *   Unsigned bit. 無符號標誌位. 當這一位拉高時, 對 可拓展原數據 進行0拓展, 否則將會使用符號拓展
  *
  * @param `[3]`
  *   Pre Load. 預取標誌位. 拉高這一位時, 表示需要預取.
  *
  * @param `[1:0]`
  *   Byte bits. 字節映射位. 代表操作的字節數.
  */
object LSUCtrl {
  def lb = "b0000_0_00".U
  def lh = "b0000_0_01".U
  def lw = "b0000_0_10".U
  def ld = "b0000_0_11".U
  def lbu = "b0010_0_00".U
  def lhu = "b0010_0_01".U
  def lwu = "b0010_0_10".U

  def sb = "b1000_0_00".U
  def sh = "b1000_0_01".U
  def sw = "b1000_0_10".U
  def sd = "b1000_0_11".U

  def lr = "b0100_000".U
  def sc = "b1100_000".U

  def preld = "b0001_0_00".U

  def isStore(ctrl: UInt) = ctrl(6)
  def isLoad(ctrl: UInt) = !ctrl(6)
  def isAtom(ctrl: UInt) = ctrl(5)
  def isUnsigned(ctrl: UInt) = ctrl(4)
  def isPreld(ctrl: UInt) = ctrl(3)
  def getEncoded(ctrl: UInt) = ctrl(1, 0)

  def needMemRead(ctrl: UInt): Bool = isLoad(
    ctrl
  )
  def needMemWrite(ctrl: UInt): Bool = isStore(
    ctrl
  )
}
//
//object MEMOpID {
//	def idle	= "b0000_000".U
//	def load	= "b0001_001".U
//	def store	= "b0001_010".U
//	def storec	= "b0010_010".U
//	def amo		= "b0001_111".U
//	def lr		= "b0001_101".U
//	def sc		= "b0001_110".U
//	def tlb		= "b0100_001".U
//	def vload	= "b1000_001".U
//	def vstore	= "b1000_010".U
//
//	def needLoad(memop: UInt) = memop(0)
//	def needStore(memop: UInt) = memop(1)
//	def needAlu(memop: UInt) = memop(2)
//	def commitToCDB(memop: UInt) = memop(3)
//	def commitToSTQ(memop: UInt) = memop(4)
//	def commitToTLB(memop: UInt) = memop(5)
//	def commitToVPU(memop: UInt) = memop(6)
//}

trait HasLSUConst {
  val IndependentAddrCalcState = false
  val moqSize = 8
  val storeQueueSize = 8
}

//class LSUIO extends FuCtrlIO {
//	val wdata = Input(UInt(XLEN.W))
//	val mispredictRec = Flipped(new MispredictRecIO)
//	val stMaskIn = Input(UInt(robSize.W))
////	val robAllocate = Input(Valid(UInt(log2Up(robSize).W)))
////	val uopIn = Input(new RenameDecodeIO)
////	val uopOut = Output(new RenameDecodeIO)
////	val isMMIO = Output(Bool())
//	val exceptionVec = Output(Vec(16, Bool()))
//	val scommit = Input(Bool())
////	val commitStoreToCDB = Output(Bool())
//	val haveUnfinishedStore = Output(Bool())
//	val flush = Input(Bool())
//}
//
//class MemReq extends ModuleBundle {
//	val addr = UInt(VAddrBits.W)
//	val size = UInt(2.W)
//	val wdata = UInt(XLEN.W)
//	val wmask = UInt(8.W)
//	val cmd = UInt(4.W)
//	val user = new DCacheUserBundle
//	val valid = Bool()
//}
//
//// Out Of Order Load/Store Unit
//class LSU extends MarCoreModule with HasLSUConst {
//	val io = IO(new LSUIO)
//	val (valid, srcA, srcB, ctrl) = (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
//	def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt, dtlbPF: Bool): UInt = {
//		this.valid := valid && !needMispredictionRecovery(io.uopIn.brMask)
//		this.srcA := srcA
//		this.srcB := srcB
//		this.ctrl := ctrl
//		io.out.bits
//	}
//
//	def needMispredictionRecovery(brMask: UInt) = {
//		io.mispredictRec.valid && io.mispredictRec.redirect.valid && brMask(io.mispredictRec.checkpoint)
//	}
//
//	def updateBrMask(brMask: UInt) = {
//		brMask & ~(UIntToOH(io.mispredictRec.checkpoint) && Fill(checkpointSize, io.mispredictRec.valid))
//	}
//
//	val dmem = io.dmem
//	// Gen result
//
//	// Decode
//	val instr		= io.uopIn.decode.cf.instr
//	val storeReq	= valid & LSUCtrl.isStore(ctrl)
//	val loadReq		= valid & LSUCtrl.isLoad(ctrl)
//	val atomReq		= valid & LSUCtrl.isAtom(ctrl)
//	val funct3		= instr(14, 12)
//
//	// Atom LR/SC Control Bits
//	// Needn't Write Now
//
//	// PF signal from TLB
//	// TODO: add a TLB bus instead of using BoringUtils.addSink/Src
//	//TODO
//
//	val addr = Mux(atomReq || lrReq || scReq, srcA, srcA + srcB)
//	val data = io.uopIn.decode.data.srcB
//	val size = Mux(LSUOpType.isAtom(func), Mux(atomWidthW, "b10".U, "b11".U), func(1, 0))
//	val memop = Wire(UInt(7.W))
//	memop := Cat (
//		false.B, // commitToVPU
//		false.B, // commitToTLB
//		false.B, // commitToSTQ
//		io.in.valid, // commitToCDB
//		amoReq, // needAlu
//		(storeReq || amoReq || scReq && !scInvalid), // needStore
//		(loadReq || amoReq || lrReq) // needLoad
//	)
//	// If a LSU inst triggers an exception, it has no difference compared with other normal insts
//	// except it will be canceled by redirect bit
//}
