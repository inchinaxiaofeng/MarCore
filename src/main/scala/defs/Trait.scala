package defs

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config._
import settings._

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

/** MarCore通用參數
  */
trait HasMarCoreParameter {
  // 1. 声明需求：混入我的类，必须在其作用域内有一个 implicit p
  // 这里可以是抽象的 (abstract)，等着混入者去实现
  implicit val p: MarCoreConfig

  /** RF 相关 */
  val NRReg = p.ISA.numGPR

  /** 機器字長 */
  val XLEN = p.ISA.xlen // 2. 定义逻辑：既然我有 p 了，我就能算出 XLEN
  val AddrBits = XLEN // 芯片内使用
  val DataBits = XLEN
  val DataBytes = DataBits / 8

  val VAddrBits = XLEN // Based on 分页
  val PAddrBits = 32 // PAddrBits is Physical Memory address bits

  val HasICache = p.Mem.HasICache
  val HasDCache = p.Mem.HasDCache

  val EnableMultiIssue = p.Core.EnableMultiIssue
  val EnableOutOfOrderExec = p.Core.EnableOutOfOrderExec
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
