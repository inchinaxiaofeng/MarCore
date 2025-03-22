package top.chiplab

import chisel3._
import circt.stage._
import chisel3.util._

import defs._
import utils._
import units.mips._
import top.Settings
import bus.cacheBus._
import bus.debugBus.{DebugBus, DebugBusCrossbar1toN}
import module.cache._

class Core(implicit val p: MarCoreConfig) extends MarCoreModule {
	require(XLEN == 32)
	require(VAddrBits == 32)
	require(AddrBits == 32)
	implicit val moduleName: String = this.name
	class MarCoreIO extends Bundle {
		val imem = new CacheBus()
		val dmem = new CacheBus()
		val mmio = new CacheBus()
		val difftest_commit = Decoupled(new CommitIO_MIPS)
		val difftest_instr = Output(UInt(XLEN.W))
		val difftest_redirect = new RedirectIO
	}
	val io = IO(new MarCoreIO)

	// Frontend
	val frontend = (Settings.get("IsRV32"), Settings.get("EnableOutOfOrderExec")) match {
//		case (true, _)		=> Module(new Frontend_embedded)
		case (false, _)		=> Module(new Frontend_embedded)
	}

	// Backend
	if (EnableOutOfOrderExec) {
		// TODO
	} else {
		val mmioXbar = Module(new CacheBusCrossbarNto1(2))
		val backend = Module(new Backend_inorder)

		val itlb = Reg(Bool())
		val dtlb = Reg(Bool())

		io.dmem <> Cache_MIPS(in = backend.io.dmem, mmio = mmioXbar.io.in.drop(1), flush = "b00".U, empty = dtlb, swapPolicy = Settings.getString("DCacheSwapPolicy"), enable = HasDCache)(CacheConfig(ro = false, name = "dcache", userBits = DCacheUserBundleWidth, cacheLevel = 2))
		io.imem <> Cache_MIPS(in = frontend.io.imem, mmio = mmioXbar.io.in.take(1), flush = Fill(2, frontend.io.flushVec(0)), empty = itlb, swapPolicy = Settings.getString("ICacheSwapPolicy"), enable = HasICache)(CacheConfig(ro = true, name = "icache", userBits = ICacheUserBundleWidth, cacheLevel = 2))

		PipelineVector2Connect(new DecodeIO_MIPS, frontend.io.out(0), frontend.io.out(1), backend.io.in(0), backend.io.in(1), frontend.io.flushVec(1), 1)

		// redirect
		frontend.io.redirect <> backend.io.redirect
		frontend.io.bpuUpdate <> backend.io.bpuUpdate
		backend.io.flush := frontend.io.flushVec(3, 2)

		io.mmio <> mmioXbar.io.out

		io.difftest_commit <> backend.io.difftest_commit
		io.difftest_instr <> backend.io.difftest_instr
		io.difftest_redirect <> backend.io.difftest_redirect
	}
}
