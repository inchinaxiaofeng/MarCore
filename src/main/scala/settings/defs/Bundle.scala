package defs

import chisel3._
import chisel3.util._

import defs._

import module._
import java.rmi.server.UID

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

/** 重定向包. 定義重定向信號
  */
class RedirectIO extends MarCoreBundle {

  /** 重定向的地址
    */
  val target = Output(UInt(VAddrBits.W))

  /** 重定向的類型
    *
    * 1: branch mispredict: only need to flush frontend.
    *
    * 0: others: flush the whole pipeline
    */
  val rtype = Output(
    UInt(1.W)
  )

  /** 拉高時, 重定向包信號有效.
    */
  val valid = Output(Bool())
}

class MispredictRecIO extends MarCoreBundle {
  val redirect = new RedirectIO
  val valid = Output(Bool())
  val checkpoint = Output(UInt(brTagWidth.W))
  val prfidx = Output(UInt(prfAddrWidth.W))
}

class RegsDiffIO(val num: Int = 0) extends MarCoreBundle {
  val regs = Output(Vec(num, UInt(XLEN.W)))
}
