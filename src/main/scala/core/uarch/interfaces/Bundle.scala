package core.uarch.interfaces

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import core.isa.{FuCtrl, FuType, SrcType}

class ForwardIO extends MarCoreBundle {
  val valid = Output(Bool())
  val wb = new WriteBackIO
  val fuType = Output(FuType())
}

class FuCtrlIO extends MarCoreBundle {
  val in = Flipped(Decoupled(new Bundle {
    val srcA = Output(UInt(XLEN.W))
    val srcB = Output(UInt(XLEN.W))
    val ctrl = Output(FuCtrl())
  }))
  val out = Decoupled(Output(UInt(XLEN.W)))
}

/** 控制信號流
  *
  * 通過隨流水傳遞控制信號流, 任何一個模塊都可以訪問其中的內容, 做出行動.
  *
  * 依託於Chisel的優化, 我們可以將所有信號都打包進一個Bundle中, 當沒有被使用時, 會被優化拋棄.
  */
class CtrlFlowIO extends MarCoreBundle {
  val instr = Output(UInt(64.W))
  val pc = Output(UInt(VAddrBits.W))

  /** Predicted Next Program Counter
    *
    * 傳遞前端預測的PC值, 用於對分支預測進行糾錯.
    */
  val pnpc = Output(UInt(VAddrBits.W))
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
  val imm = Output(UInt(XLEN.W))
}

class WriteBackIO extends MarCoreBundle {
  val rfWen = Output(Bool())
  val rfDest = Output(UInt(5.W))
  val rfData = Output(UInt(XLEN.W))
}

class DecodeIO extends MarCoreBundle {
  val cf = new CtrlFlowIO
  val ctrl = new CtrlSignalIO
  val data = new DataSrcIO
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
