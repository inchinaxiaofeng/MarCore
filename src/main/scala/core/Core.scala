package core

import chisel3._
import circt.stage._
import chisel3.util._

import defs._
import utils._
import blackbox._

import bus.cacheBus._
import core.uarch.interfaces.DecodeIO
import core.frontend.Frontend_embedded
import core.backend.Backend_inorder
import core.mem.cache.{Cache, CacheConfig, RandPolicy}

class Core(implicit val p: MarCoreConfig) extends MarCoreModule {
  implicit val moduleName: String = this.name
  class MarCoreIO extends Bundle {
    val imem = new CacheBus()
    val dmem = new CacheBus()
    val mmio = new CacheBus()
  }
  val io = IO(new MarCoreIO)

  val mmioXbar = Module(new CacheBusCrossbarNto1(2))
  val itlb = Reg(Bool())
  val dtlb = Reg(Bool())

  val frontend = if (p.Core.EnableMultiIssue) {
    Info("EnableMultiIssue not impl yet.")
    Module(new Frontend_embedded())
  } else {
    Module(new Frontend_embedded())
  }
  val backend = if (p.Core.EnableOutOfOrderExec) {
    Info("EnableOutOfOrderExec not impl yet.")
    Module(new Backend_inorder())
  } else {
    Module(new Backend_inorder())
  }

  PipelineVector2Connect(
    new DecodeIO,
    frontend.io.out(0),
    frontend.io.out(1),
    backend.io.in(0),
    backend.io.in(1),
    frontend.io.flushVec(1),
    8
  )

  frontend.io.ipf := false.B

  // redirect
  frontend.io.redirect <> backend.io.redirect
  frontend.io.bpuUpdate <> backend.io.bpuUpdate
  backend.io.flush := frontend.io.flushVec(3, 2)

  io.dmem <> Cache(
    in = backend.io.dmem,
    mmio = mmioXbar.io.in.drop(1),
    flush = "b00".U,
    empty = dtlb,
    enable = HasDCache
  )(
    CacheConfig(
      ro = false,
      name = "dcache",
      userBits = DCacheUserBundleWidth,
      cacheLevel = 2,
      sysConfig = p.System,
      policy = RandPolicy
    )
  )
  io.imem <> Cache(
    in = frontend.io.imem,
    mmio = mmioXbar.io.in.take(1),
    flush = Fill(2, frontend.io.flushVec(0)),
    empty = itlb,
    enable = HasICache
  )(
    CacheConfig(
      ro = true,
      name = "icache",
      userBits = ICacheUserBundleWidth,
      cacheLevel = 2,
      sysConfig = p.System,
      policy = RandPolicy
    )
  )

  io.mmio <> mmioXbar.io.out
}
