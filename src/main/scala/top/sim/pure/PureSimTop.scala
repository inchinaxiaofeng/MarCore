package top.sim.pure

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import defs.{MarCoreModule, MarCoreConfig}
import core.Core
import core.mem.cache.{Cache, CacheConfig, NonePolicy}
import bus.cacheBus.CacheBusCrossbarNto1
import config.{
  ISAConfig,
  SystemConfig,
  LogConfig,
  MemConfig,
  StatConfig,
  CoreConfig,
  RV32E
}
import bus.axi4.AXI4_Arbiter_MMIO
import top.io.MEMIO
import top.io.AXI4ToMemConverter

class PureSimTop32 extends Module {
  lazy val config = MarCoreConfig(
    FPGAPlatform = false,
    System = SystemConfig(resetVector = 0x0L, mmio = Seq()),
    Log = LogConfig(),
    Mem = MemConfig(),
    Stat = StatConfig(),
    Core = CoreConfig()
  )
  implicit val moduleName: String = this.name
  val io = IO(new MEMIO())
  val core = Module(new Core()(config))
  val arbiter = Module(new AXI4_Arbiter_MMIO)
  val axi4ToMem = Module(new AXI4ToMemConverter(cnt = 4))

  core.io.imem.toAXI4(isFromCache = true) <> arbiter.InstFetch
  core.io.dmem.toAXI4(isFromCache = true) <> arbiter.LoadStore
  core.io.mmio.toAXI4(isFromCache = false) <> arbiter.MMIO

  arbiter.Arbiter <> axi4ToMem.io

  io <> axi4ToMem.mem
}
