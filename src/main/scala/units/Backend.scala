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

class Backend_inorder(implicit val p: MarCoreConfig) extends MarCoreModule {
	implicit val moduleName: String = this.name
	val io = IO(new Bundle {
		val in = Vec(2, Flipped(Decoupled(new DecodeIO)))
		val flush = Input(UInt(2.W))
		val dmem = new CacheBus
		val redirect = new RedirectIO
		val bpuUpdate = new BPUUpdate
		// DiffTest
		val gpr = if (Settings.get("EnableDifftest") && Settings.get("DiffTestGPR")) Some(new RegsDiffIO(num = 32)) else None
		val csr = if (Settings.get("EnableDifftest") && Settings.get("DiffTestCSR")) Some(new RegsDiffIO(num = 4)) else None
		val difftest_commit = if (Settings.get("EnableDifftest")) Some(Decoupled(new CommitIO)) else None
		val difftest_redirect = if (Settings.get("EnableDifftest")) Some(new RedirectIO) else None
		val difftest_instr = if (Settings.get("EnableDifftest")) Some(Output(UInt(XLEN.W))) else None
	})

	val isu = Module(new ISU)
	val exu = Module(new EXU)
	val wbu = Module(new WBU)

	PipelineConnect(isu.io.out, exu.io.in, exu.io.out.fire, io.flush(0))
	PipelineConnect(exu.io.out, wbu.io.in, true.B, io.flush(1))

	isu.io.in <> io.in

	isu.io.flush := io.flush(0)
	exu.io.flush := io.flush(1)

	isu.io.wb <> wbu.io.wb
	io.redirect <> wbu.io.redirect
	// forward
	isu.io.forward <> exu.io.forward
	io.dmem <> exu.io.dmem

	io.bpuUpdate <> exu.io.bpuUpdate

	Debug("------------------------ Backend ------------------------\n")
	Debug("flush = %b, {{{ ==%x%x>[isu]<%x---%x>[exu]<%x---%x>[wbu] }}}\n", io.flush.asUInt, isu.io.in(0).ready, isu.io.in(1).ready, isu.io.out.valid, exu.io.in.ready, exu.io.out.valid, wbu.io.in.ready)
	Debug(isu.io.out.valid, "ISU: pc = 0x%x instr = 0x%x pnpc = 0x%x\n", isu.io.out.bits.cf.pc, isu.io.out.bits.cf.instr, isu.io.out.bits.cf.pnpc)
	Debug(exu.io.in.valid, "EXU: pc = 0x%x instr = 0x%x pnpc = 0x%x\n", exu.io.in.bits.cf.pc, exu.io.in.bits.cf.instr, exu.io.in.bits.cf.pnpc)
	Debug(wbu.io.in.valid, "WBU: pc = 0x%x instr = 0x%x pnpc = 0x%x\n", wbu.io.in.bits.decode.cf.pc, wbu.io.in.bits.decode.cf.instr, wbu.io.in.bits.decode.cf.pnpc)

	if (Settings.get("EnableDifftest")) {
    if (Settings.get("DiffTestGPR")) io.gpr.get <> isu.io.gpr.get
  	if (Settings.get("DiffTestCSR")) io.csr.get <> RegNext(exu.io.csr.get)
		io.difftest_commit.get <> wbu.io.difftest_commit.get
		io.difftest_instr.get <> wbu.io.difftest_instr.get
		io.difftest_redirect.get <> wbu.io.difftest_redirect.get
	}

  if (Settings.get("Statistic")) {
    val statistic_back_hunger = Module(new STATISTIC_BACK_HUNGER)
    statistic_back_hunger.io.clk := clock
    statistic_back_hunger.io.rst := reset
    statistic_back_hunger.io.isu_hunger := !isu.io.in(0).valid
    statistic_back_hunger.io.exu_hunger := !exu.io.in.valid
    statistic_back_hunger.io.wbu_hunger := !wbu.io.in.valid
  }
}
