// package units.mips
//
// import chisel3._
// import chisel3.util._
//
// import defs._
// import utils._
// import bus.cacheBus._
// import module.cache._
// import module.fu.BPUUpdate
// import module.fu.mips._
//
// class Backend_inorder(implicit val p: MarCoreConfig) extends MarCoreModule {
// 	implicit val moduleName: String = this.name
// 	val io = IO(new Bundle {
// 		val in = Vec(2, Flipped(Decoupled(new DecodeIO_MIPS)))
// 		val flush = Input(UInt(2.W))
// 		val dmem = new CacheBus
//
// 		val redirect = new RedirectIO
// 		val bpuUpdate = new BPUUpdate
// 		// DiffTest
// 		val difftest_commit = Decoupled(new CommitIO_MIPS)
// 		val difftest_redirect = new RedirectIO
// 		val difftest_instr = Output(UInt(XLEN.W))
// 	})
//
// 	val isu = Module(new ISU)
// 	val exu = Module(new EXU)
// 	val wbu = Module(new WBU)
//
// 	PipelineConnect(isu.io.out, exu.io.in, exu.io.out.fire, io.flush(0) && !exu.io.in.bits.isDelaySlot)
// 	PipelineConnect(exu.io.out, wbu.io.in, true.B, io.flush(1) && !wbu.io.in.bits.decode.isDelaySlot)
//
// 	isu.io.in <> io.in
//
// 	isu.io.flush := io.flush(0)
// 	exu.io.flush := io.flush(1)
//
// 	isu.io.wb <> wbu.io.wb
// 	io.redirect <> wbu.io.redirect
// 	// forward
// 	isu.io.forward <> exu.io.forward
// 	io.dmem <> exu.io.dmem
//
// 	io.bpuUpdate <> exu.io.bpuUpdate
//
// //	Debug("------------------------ Backend ------------------------\n")
// //	Debug("flush = %b, {{{ ==%x%x>[isu]<%x---%x>[exu]<%x---%x>[wbu] }}}\n", io.flush.asUInt, isu.io.in(0).ready, isu.io.in(1).ready, isu.io.out.valid, exu.io.in.ready, exu.io.out.valid, wbu.io.in.ready)
// //	Debug(isu.io.out.valid, "ISU: pc = 0x%x instr = 0x%x pnpc = 0x%x\n", isu.io.out.bits.cf.pc, isu.io.out.bits.cf.instr, isu.io.out.bits.cf.pnpc)
// //	Debug(exu.io.in.valid, "EXU: pc = 0x%x instr = 0x%x pnpc = 0x%x\n", exu.io.in.bits.cf.pc, exu.io.in.bits.cf.instr, exu.io.in.bits.cf.pnpc)
// //	Debug(wbu.io.in.valid, "WBU: pc = 0x%x instr = 0x%x pnpc = 0x%x\n", wbu.io.in.bits.decode.cf.pc, wbu.io.in.bits.decode.cf.instr, wbu.io.in.bits.decode.cf.pnpc)
//
// 	io.difftest_commit <> wbu.io.difftest_commit
// 	io.difftest_instr <> wbu.io.difftest_instr
// 	io.difftest_redirect <> wbu.io.difftest_redirect
// }
