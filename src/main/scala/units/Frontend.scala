package units

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.cacheBus._
import module.cache._
import module.fu.BPUUpdate
import top.Settings
import blackbox._

class FrontendIO(implicit val p: MarCoreConfig) extends Bundle with HasMarCoreConst {
	val imem = new CacheBus(userBits = ICacheUserBundleWidth)
	val out = Vec(2, Decoupled(new DecodeIO))
	val flushVec = Output(UInt(4.W))
	val redirect = Flipped(new RedirectIO)
	val bpFlush = Output(Bool())
	val ipf = Input(Bool())
	val bpuUpdate = Flipped(new BPUUpdate)
}

class Frontend_embedded(implicit val p: MarCoreConfig) extends MarCoreModule {
	implicit val moduleName: String = this.name
	val io = IO(new FrontendIO)
	val ifu = Module(new IFU_embedded)
	val idu = Module(new IDU)

	PipelineConnect(ifu.io.out, idu.io.in(0), idu.io.out(0).fire, ifu.io.flushVec(0))
	idu.io.in(1) := DontCare

	io.out <> idu.io.out
	io.redirect <> ifu.io.redirect
	io.flushVec <> ifu.io.flushVec
	io.bpFlush <> ifu.io.bpFlush
	io.ipf <> ifu.io.ipf
	io.imem <> ifu.io.imem
	io.imem.req.bits.user.foreach { userIO =>
		userIO := ifu.io.imem.req.bits.user.get
	}

	ifu.io.bpuUpdate <> io.bpuUpdate

	Info("------------------------ Frontend ------------------------\n")
	Info("flush = %b, {{{ [ifu]<%x--=%x%x>[idu]<%x%x== }}}\n",
		ifu.io.flushVec.asUInt, ifu.io.out.valid,
		idu.io.in(0).ready, idu.io.in(1).ready,
		idu.io.out(0).valid, idu.io.out(1).valid)
	Info(ifu.io.out.valid, "IFU: pc = 0x%x, instr = 0x%x\n", ifu.io.out.bits.pc, ifu.io.out.bits.instr)
	Info(idu.io.in(0).valid, "IDU1: pc = 0x%x, instr = 0x%x, pnpc = 0x%x\n", idu.io.in(0).bits.pc, idu.io.in(0).bits.instr, idu.io.in(0).bits.pnpc)
	Info(idu.io.in(1).valid, "IDU2: pc = 0x%x, instr = 0x%x, pnpc = 0x%x\n", idu.io.in(1).bits.pc, idu.io.in(1).bits.instr, idu.io.in(1).bits.pnpc)

  if (Settings.get("Statistic")) {
    val statistic_front_hunger = Module(new STATISTIC_FRONT_HUNGER)
    statistic_front_hunger.io.clk := clock
    statistic_front_hunger.io.rst := reset
    statistic_front_hunger.io.ifu_hunger := !ifu.io.imem.resp.valid
    statistic_front_hunger.io.idu_hunger := !idu.io.in(0).valid
  }
}

//class Frontend_inorder(implicit val p: MarCoreConfig) extends MarCoreModule {
//	implicit val moduleName: String = this.name
//	val io = IO(new FrontendIO)
//	val ifu = Module(new IFU_inorder)
//}
