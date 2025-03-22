package top

import chisel3._
import circt.stage._
import chisel3.util._

import defs._
import units._
import utils._
import bus.cacheBus._
import bus.debugBus.{DebugBus, DebugBusCrossbar1toN}
import module.cache._
import blackbox._

class Core(implicit val p: MarCoreConfig) extends MarCoreModule {
	require(XLEN == 64)
	implicit val moduleName: String = this.name
	class MarCoreIO extends Bundle {
		val imem = new CacheBus()
		val dmem = new CacheBus()
		val mmio = new CacheBus
		val gpr = if (Settings.get("EnableDifftest")) Some(new RegsDiffIO(num = 32)) else None
		val csr = if (Settings.get("EnableDifftest")) Some(new RegsDiffIO(num = 4)) else None
		val difftest_commit = if (Settings.get("EnableDifftest")) Some(Decoupled(new CommitIO)) else None
		val difftest_instr = if (Settings.get("EnableDifftest")) Some(Output(UInt(XLEN.W))) else None
		val difftest_redirect = if (Settings.get("EnableDifftest")) Some(new RedirectIO) else None
//		val debugBus = if (Settings.get("EnableDebugBus")) Some(Flipped(new DebugBus)) else None
	}
	val io = IO(new MarCoreIO)

	// Frontend
	val frontend = (Settings.get("EnableOutOfOrderExec")) match {
		case (false)	=> Module(new Frontend_embedded)
	}
// FIXME: 删除了DEBUG BUS
	// Backend
	if (EnableOutOfOrderExec) {
		// TODO
	} else {
		val mmioXbar = Module(new CacheBusCrossbarNto1(2))
		val backend = Module(new Backend_inorder)

		val itlb = Reg(Bool())
		val dtlb = Reg(Bool())
//  	val xbarDebug = if (Settings.get("EnableDebugBus")) Some(Module(new DebugBusCrossbar1toN(2))) else None
//    if (Settings.get("EnableDebugBus")) {
//  		// Debug Bus
//  		xbarDebug.get.io.outs(0) := DontCare
//  //		xbarDebug.io.outs(0).resp.valid := false.B
//  		xbarDebug.get.io.outs(1) := DontCare
//  		xbarDebug.get.io.in <> io.debugBus.get
//    }

//		io.dmem <> Cache(in = backend.io.dmem, mmio = mmioXbar.io.in.drop(1), flush = "b00".U, empty = dtlb, swapPolicy = Settings.getString("DCacheSwapPolicy"), enable = HasDCache, debugBus = xbarDebug.io.outs(1))(CacheConfig(ro = false, name = "dcache", userBits = DCacheUserBundleWidth, cacheLevel = 2))
//		io.imem <> Cache(in = frontend.io.imem, mmio = mmioXbar.io.in.take(1), flush = Fill(2, frontend.io.flushVec(0)), empty = itlb, swapPolicy = Settings.getString("ICacheSwapPolicy"), enable = HasICache, debugBus = xbarDebug.io.outs(0))(CacheConfig(ro = true, name = "icache", userBits = ICacheUserBundleWidth, cacheLevel = 2))
//
		io.dmem <> Cache(in = backend.io.dmem, mmio = mmioXbar.io.in.drop(1), flush = "b00".U, empty = dtlb, swapPolicy = Settings.getString("DCacheSwapPolicy"), enable = HasDCache)(CacheConfig(ro = false, name = "dcache", userBits = DCacheUserBundleWidth, cacheLevel = 2))
		io.imem <> Cache(in = frontend.io.imem, mmio = mmioXbar.io.in.take(1), flush = Fill(2, frontend.io.flushVec(0)), empty = itlb, swapPolicy = Settings.getString("ICacheSwapPolicy"), enable = HasICache)(CacheConfig(ro = true, name = "icache", userBits = ICacheUserBundleWidth, cacheLevel = 2))

		PipelineVector2Connect(new DecodeIO, frontend.io.out(0), frontend.io.out(1), backend.io.in(0), backend.io.in(1), frontend.io.flushVec(1), 8)

		frontend.io.ipf := false.B // Fixme maybe

		// redirect
		frontend.io.redirect <> backend.io.redirect
		frontend.io.bpuUpdate <> backend.io.bpuUpdate
		backend.io.flush := frontend.io.flushVec(3, 2)
		Info("flush = %b, 0: frontend:(%d,%d), backend:(%d,%d); " + "1: frontend:(%d,%d), backend:(%d,%d)\n", frontend.io.flushVec.asUInt, frontend.io.out(0).valid, frontend.io.out(0).ready, backend.io.in(0).valid, backend.io.in(0).ready, frontend.io.out(1).valid, frontend.io.out(1).ready, backend.io.in(1).valid, backend.io.in(1).ready)

		io.mmio <> mmioXbar.io.out
		if (Settings.get("EnableDifftest")) {
			if (Settings.get("DiffTestGPR")) io.gpr.get <> backend.io.gpr.get
			if (Settings.get("DiffTestCSR")) io.csr.get <> backend.io.csr.get
			io.difftest_commit.get <> backend.io.difftest_commit.get
			io.difftest_instr.get <> backend.io.difftest_instr.get
			io.difftest_redirect.get <> backend.io.difftest_redirect.get
		}

		if (Settings.get("TraceLoadStore")) {
			val iReq  = io.imem.req
			val iResp = io.imem.resp
			val dReq  = io.dmem.req
			val dResp = io.dmem.resp
			Info("[Inst] Req(%d,%d)\t   addr 0x%x len %d size %d user %x id %d; Resp(%d,%d) data 0x%x last %d user %x id %d\n", iReq.valid, iReq.ready, iReq.bits.addr, iReq.bits.len, iReq.bits.size, iReq.bits.user.getOrElse(0.U), iReq.bits.id.getOrElse(0.U), iResp.valid, iResp.ready, iResp.bits.data, iResp.bits.last, iResp.bits.user.getOrElse(0.U), iResp.bits.id.getOrElse(0.U))
			Info("[Data] Req(%d,%d) isW %d addr 0x%x len %d size %d data 0x%x strb %b last %d user %d id %d; Resp(%d,%d) isW %d data 0x%x last %d resp %d user %x id %d\n", dReq.valid, dReq.ready, dReq.bits.write, dReq.bits.addr, dReq.bits.len, dReq.bits.size, dReq.bits.data, dReq.bits.strb, dReq.bits.last, dReq.bits.user.getOrElse(0.U), dReq.bits.id.getOrElse(0.U), dResp.valid, dResp.ready, dResp.bits.write, dResp.bits.data, dResp.bits.last, dResp.bits.user.getOrElse(0.U), dResp.bits.id.getOrElse(0.U))
		}
		if (Settings.get("TraceMMIO")) {
			val mReq  = io.mmio.req
			val mResp = io.mmio.resp
			Info("[MMIO] Req(%d,%d) isW %d addr 0x%x len %d size %d data 0x%x strb %b last %d user %d id %d; Resp(%d,%d) isW %d data 0x%x last %d resp %d user %x id %d\n", mReq.valid, mReq.ready, mReq.bits.write, mReq.bits.addr, mReq.bits.len, mReq.bits.size, mReq.bits.data, mReq.bits.strb, mReq.bits.last, mReq.bits.user.getOrElse(0.U), mReq.bits.id.getOrElse(0.U), mResp.valid, mResp.ready, mResp.bits.write, mResp.bits.data, mResp.bits.last, mResp.bits.user.getOrElse(0.U), mResp.bits.id.getOrElse(0.U))
		}
//    if (Settings.get("Statistic")) {
//      val statistic_fb_hunger = Module(new STATISTIC_FB_HUNGER)
//      statistic_fb_hunger.io.clk := clock
//      statistic_fb_hunger.io.rst := reset
//      statistic_fb_hunger.io.front_hunger := 
//    }
	}
}
