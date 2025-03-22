package defs

import chisel3._
import chisel3.util._

import defs._

import module._
import java.rmi.server.UID

// UARR IO, if needed should be inited in SimTop ForwardIO
// If not needed, just hardwire all output to 0
class UARTIO extends Bundle {
  val out = new Bundle {
    val valid = Output(Bool())
    val ch = Output(UInt(8.W))
  }
  val in = new Bundle {
    val valid = Output(Bool())
    val ch = Input(UInt(8.W))
  }
}

class ForwardIO extends MarCoreBundle {
	val valid = Output(Bool())
	val wb = new WriteBackIO
	val fuType = Output(FuType())
}

class MMUIO extends MarCoreBundle {
	val priviledgeMode = Input(UInt(2.W))
	val status_sum = Input(Bool())
	val status_mxr = Input(Bool())

	val loadPF = Output(Bool())
	val storePF = Output(Bool())
	val addr = Output(UInt(VAddrBits.W))

	def isPF() = loadPF || storePF
}

class MemMMUIO extends MarCoreBundle {
	val imem = new MMUIO
	val dmem = new MMUIO
}

class FuCtrlIO extends MarCoreBundle {
	val in = Flipped(Decoupled(new Bundle {
		val srcA = Output(UInt(XLEN.W))
		val srcB = Output(UInt(XLEN.W))
		val ctrl = Output(FuCtrl())
	}))
	val out = Decoupled(Output(UInt(XLEN.W)))
}

class RedirectIO extends MarCoreBundle {
	val target = Output(UInt(VAddrBits.W))
	val rtype = Output(UInt(1.W)) // 1: branch mispredict: only need to flush frontend. 0: others: flush the whole pipeline
	val valid = Output(Bool())
}

class MispredictRecIO extends MarCoreBundle {
    val redirect = new RedirectIO
    val valid = Output(Bool())
    val checkpoint = Output(UInt(brTagWidth.W))
    val prfidx = Output(UInt(prfAddrWidth.W))
}

class CtrlFlowIO extends MarCoreBundle {
	val instr = Output(UInt(64.W))
	val pc = Output(UInt(VAddrBits.W))
	val pnpc = Output(UInt(VAddrBits.W)) // Predicted Next Program Counter
	val redirect = new RedirectIO
	val exceptionVec = Output(Vec(16, Bool()))
	val intrVec = Output(Vec(12, Bool()))
	val brIdx = Output(UInt(4.W))
	val isRVC = Output(Bool())
	val crossPageIPFFix = Output(Bool())
	val runahead_checkpoint_id = Output(UInt(64.W))
	val isBranch = Output(Bool())
}

class CtrlSignalIO extends MarCoreBundle {
	val srcAType = Output(SrcType())
	val srcBType = Output(SrcType())
	val fuType = Output(FuType())
	val fuCtrl = Output(FuCtrl())
	val rfSrcA = Output(UInt(5.W))
	val rfSrcB = Output(UInt(5.W))
	val rfWen = Output(Bool())
	val rfDest = Output(UInt(5.W))
	val isMarCoreTrap = Output(Bool())
	val isSrcAForward = Output(Bool())
	val isSrcBForward = Output(Bool())
	val noSpecExec = Output(Bool())
	val isBlocked = Output(Bool())
}

class DataSrcIO extends MarCoreBundle {
	val srcA = Output(UInt(XLEN.W))
	val srcB = Output(UInt(XLEN.W))
	val imm  = Output(UInt(XLEN.W))
}

class DecodeIO extends MarCoreBundle {
	val cf = new CtrlFlowIO
	val ctrl = new CtrlSignalIO
	val data = new DataSrcIO
}

class WriteBackIO extends MarCoreBundle {
	val rfWen = Output(Bool())
	val rfDest = Output(UInt(5.W))
	val rfData = Output(UInt(XLEN.W))
}

class CommitIO extends MarCoreBundle {
	val decode = new DecodeIO
	val isMMIO = Output(Bool())
	val intrNO = Output(UInt(XLEN.W))
	val commits = Output(Vec(FuType.num, UInt(XLEN.W)))
}

class OOCommitIO extends MarCoreBundle {
	val decode = new DecodeIO
	val idMMIO = Output(Bool())
	val intrNO = Output(UInt(XLEN.W))
	val commits = Output(Vec(FuType.num, UInt(XLEN.W)))
	val prfidx = Output(UInt(prfAddrWidth.W)) // also as robidx
	val exception = Output(Bool())
	val store = Output(Bool())
	val brMask = Output(UInt(checkpointSize.W))
}

class RegsDiffIO(val num: Int = 0) extends MarCoreBundle {
	val regs = Output(Vec(num, UInt(XLEN.W)))
}

// MIPS

class CtrlFlowIO_MIPS extends MarCoreBundle {
	val instr = Output(UInt(64.W))
	val pc = Output(UInt(VAddrBits.W))
	val pnpc = Output(UInt(VAddrBits.W)) // Predicted Next Program Counter
	val redirect = new RedirectIO
//	val exceptionVec = Output(Vec(16, Bool()))
//	val intrVec = Output(Vec(12, Bool()))
	val brIdx = Output(UInt(4.W))
//	val isRVC = Output(Bool())
//	val crossPageIPFFix = Output(Bool())
//	val runahead_checkpoint_id = Output(UInt(64.W))
	val isBranch = Output(Bool())
}

class DecodeIO_MIPS extends MarCoreBundle {
	val cf = new CtrlFlowIO_MIPS
	val ctrl = new CtrlSignalIO
	val data = new DataSrcIO
	val isDelaySlot = Output(Bool())
}

class CommitIO_MIPS extends MarCoreBundle {
	val decode = new DecodeIO_MIPS
	val isMMIO = Output(Bool())
//	val intrNO = Output(UInt(XLEN.W))
	val commits = Output(Vec(FuType.num, UInt(XLEN.W)))
}
