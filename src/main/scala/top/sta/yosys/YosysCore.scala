package top.sta.yosys

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
import telemetry.difftest.{DiffEssentialIO}
import config.DiffConfig
import telemetry.difftest.DiffRegIO
import bus.axi4.AXI4

class YosysSTACore extends RawModule {
  val clk = IO(Input(Clock()))
  val reset = IO(Input(Bool()))
  val io = IO(new AXI4)

  lazy val config = MarCoreConfig(
    FPGAPlatform = false,
    System = SystemConfig(resetVector = 0x0L, mmio = Seq()),
    Log = LogConfig(),
    Mem = MemConfig(),
    Stat = StatConfig(),
    Core = CoreConfig(),
    Diff = DiffConfig(diffRegFile = true)
  )
  val core = withClockAndReset(clk, reset) { Module(new Core()(config)) }
  val arbiter = withClockAndReset(clk, reset) { Module(new AXI4_Arbiter_MMIO) }
  // val bridge = Module(new soc_axi_sram_bridge())
  withClockAndReset(clk, reset) {
    core.io.imem.toAXI4(isFromCache = true) <> arbiter.InstFetch
    core.io.dmem.toAXI4(isFromCache = true) <> arbiter.LoadStore
    core.io.mmio.toAXI4(isFromCache = false) <> arbiter.MMIO
  }
  io <> arbiter.Arbiter
}
