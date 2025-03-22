// package blackbox
// 
// import chisel3._
// import chisel3.util._
// 
// import defs._
// import utils._
// import top.Settings
// 
// class difftest_io extends MarCoreModule {
// 	val io = IO(new Bundle {
// 		val difftest_commit = Decoupled(new CommitIO)
// 		val difftest_instr = Output(UInt(XLEN.W))
// 		val difftest_redirect = new RedirectIO
// 	})
// }
// 
// // use DPI-C to update env_cpu info.
// class difftest_dpi_c extends BlackBox {
// 	val io = IO(new Bundle{
// 		val aclk	= Input(Clock())
// 		val areset	= Input(Bool())
// 
// 	})
// }
