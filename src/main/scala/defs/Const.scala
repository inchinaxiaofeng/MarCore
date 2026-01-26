package defs

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config._

trait HasBackendConst {
  val robSize = 16
  val robWidth = 2
  val robInstCapacity = robSize * robWidth
  val checkpointSize = 4 // register map checkpoint size
  val brTagWidth = log2Up(checkpointSize)
  val prfAddrWidth =
    log2Up(robSize) + log2Up(robWidth) // physical rf addr width

  val DispatchWidth = 2
  val CommitWidth = 2
  val RetireWidth = 2

  val enablCheckpoint = true
}

// NEW
trait HasMarCoreConst extends HasMarCoreParameter {
  val CacheReadWidth = 64
  val DCacheUserBundleWidth = 0
  val ICacheUserBundleWidth = VAddrBits * 2 // For PC and NPC
  // 原来架构的东西, 现在暂时没有用
  val IndependentBru =
    false // if (Settings.get("EnableOutOfOrderExec")) true else false
}
