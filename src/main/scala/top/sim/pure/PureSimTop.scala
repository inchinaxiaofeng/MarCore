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
import telemetry.difftest.{DiffEssentialIO}
import config.DiffConfig
import telemetry.difftest.DiffRegIO
import utils.LogLevel
import utils.LogUtil

class PureSimTop32 extends Module {
  LogUtil.setLogLevel(LogLevel.DEBUG)
  lazy val config = MarCoreConfig(
    FPGAPlatform = false,
    System = SystemConfig(mmio = Seq()),
    Log = LogConfig(LogCache = true),
    Mem = MemConfig(),
    Stat = StatConfig(),
    Core = CoreConfig(),
    Diff = DiffConfig(diffRegFile = true)
  )
  implicit val moduleName: String = this.name
  val io = IO(Flipped(new MEMIO()))
  val core = Module(new Core()(config))
  val arbiter = Module(new AXI4_Arbiter_MMIO)
  val axi4ToMem = Module(new AXI4ToMemConverter(cnt = 4))

  core.io.imem.toAXI4(isFromCache = true) <> arbiter.InstFetch
  core.io.dmem.toAXI4(isFromCache = true) <> arbiter.LoadStore
  core.io.mmio.toAXI4(isFromCache = false) <> arbiter.MMIO

  arbiter.Arbiter <> axi4ToMem.io

  io <> axi4ToMem.mem

  // Difftest
  if (config.Diff.isEnabled) {
    val diffEssenIO = IO(Output(new DiffEssentialIO))
    diffEssenIO.valid := BoringUtils.tapAndRead(core.backend.wbu.io.in.valid)
    diffEssenIO.pc := BoringUtils.tapAndRead(
      core.backend.wbu.io.in.bits.decode.cf.pc
    )
    diffEssenIO.npc := BoringUtils.tapAndRead(
      core.backend.wbu.io.in.bits.decode.cf.pnpc
    ) // 当前架构之中, 只要提交了, 其实就是已经可以用的了.
    diffEssenIO.inst := BoringUtils.tapAndRead(
      core.backend.wbu.io.in.bits.decode.cf.instr
    )
    diffEssenIO.isRVC := false.B // 并没有添加相关架构的支持

    if (config.Diff.diffRegFile) {
      val diffRegIO = IO(Output(new DiffRegIO))
      diffRegIO.wen := BoringUtils.tapAndRead(core.backend.wbu.io.wb.rfWen)
      diffRegIO.wdata := BoringUtils.tapAndRead(core.backend.wbu.io.wb.rfData)
      diffRegIO.wdest := BoringUtils.tapAndRead(core.backend.wbu.io.wb.rfDest)
    }

    if (config.Diff.diffSystem) {
      assert
    }

    if (config.Diff.diffMemory) {
      assert
    }
  }
}
