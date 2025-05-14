// package top.chiplab
//
// import chisel3._
// import chisel3.util._
// import chisel3.util.experimental.BoringUtils
//
// import bus.axi4.{AXI4, AXI4_Arbiter_MMIO}
// import defs._
// import utils._
// import top.Settings
// import top.chiplab._
// import bus.cacheBus._
//
// /** Top module for `ChipLab`, please dont change!.
//   *
//   * 這個是用於提供 `ChipLab` 使用的模塊接口。 當生成 Verilog 的時候，其模塊接口當與 ChipLab 保持一致。
//   *
//   * TODO: 需要通過綁定生成命名的方式，或黑盒的方式，確保這個接口穩定
//   */
// class core_top extends RawModule {
//   // 手動引入 Config 結構。
//   lazy val config = MarCoreConfig(
//     FPGAPlatform = false,
//     EnableDebug = false,
//     EnhancedLog = false
//   )
//   implicit val moduleName: String = this.name
//   val aclk = IO(Input(Clock()))
//   val aresetn = IO(Input(Bool()))
//   val intrpt = IO(Input(UInt(8.W)))
//   // NOTE: AXI Interface
//   // Read request
//   val arid = IO(Output(UInt(4.W)))
//   val araddr = IO(Output(UInt(32.W)))
//   val arlen = IO(Output(UInt(8.W)))
//   val arsize = IO(Output(UInt(3.W)))
//   val arburst = IO(Output(UInt(2.W)))
//   val arlock = IO(Output(UInt(2.W)))
//   val arcache = IO(Output(UInt(4.W)))
//   val arprot = IO(Output(UInt(3.W)))
//   val arvalid = IO(Output(UInt(1.W)))
//   val arready = IO(Input(UInt(1.W)))
//   // Read back
//   val rid = IO(Input(UInt(4.W)))
//   val rdata = IO(Input(UInt(32.W)))
//   val rresp = IO(Input(UInt(2.W)))
//   val rlast = IO(Input(UInt(1.W)))
//   val rvalid = IO(Input(UInt(1.W)))
//   val rready = IO(Output(UInt(1.W)))
//   // Write request
//   val awid = IO(Output(UInt(4.W)))
//   val awaddr = IO(Output(UInt(32.W)))
//   val awlen = IO(Output(UInt(8.W)))
//   val awsize = IO(Output(UInt(3.W)))
//   val awburst = IO(Output(UInt(2.W)))
//   val awlock = IO(Output(UInt(2.W)))
//   val awcache = IO(Output(UInt(4.W)))
//   val awprot = IO(Output(UInt(3.W)))
//   val awvalid = IO(Output(UInt(1.W)))
//   val awready = IO(Input(UInt(1.W)))
//   // Write data
//   val wid = IO(Output(UInt(4.W)))
//   val wdata = IO(Output(UInt(32.W)))
//   val wstrb = IO(Output(UInt(4.W)))
//   val wlast = IO(Output(UInt(1.W)))
//   val wvalid = IO(Output(UInt(1.W)))
//   val wready = IO(Input(UInt(1.W)))
//   // Write back
//   val bid = IO(Input(UInt(4.W)))
//   val bresp = IO(Input(UInt(2.W)))
//   val bvalid = IO(Input(UInt(1.W)))
//   val bready = IO(Output(UInt(1.W)))
//   // NOTE: Debug Info
//   val debug0_wb_pc = IO(Output(UInt(32.W)))
//   val debug0_wb_rf_wen = IO(Output(UInt(4.W)))
//   val debug0_wb_rf_wnum = IO(Output(UInt(5.W)))
//   val debug0_wb_rf_wdata = IO(Output(UInt(32.W)))
//   if (Settings.get("CPU_2CMT")) {
//     val debug1_wb_pc = IO(Output(UInt(32.W)))
//     val debug1_wb_rf_wen = IO(Output(UInt(4.W)))
//     val debug1_wb_rf_wnum = IO(Output(UInt(5.W)))
//     val debug1_wb_rf_wdata = IO(Output(UInt(32.W)))
//   }
//
//   withClockAndReset(aclk, !aresetn) {
//
//     val core = Module(new Core()(config))
//     val arbiter = Module(new AXI4_Arbiter_MMIO)
//
//     core.io.imem.toAXI4(isFromCache = true) <> arbiter.InstFetch
//     core.io.dmem.toAXI4(isFromCache = true) <> arbiter.LoadStore
//     core.io.mmio.toAXI4(isFromCache = false) <> arbiter.MMIO
//
//     // Read request
//     arid := arbiter.Arbiter.ar.bits.id
//     araddr := arbiter.Arbiter.ar.bits.addr
//     arlen := arbiter.Arbiter.ar.bits.len
//     arsize := arbiter.Arbiter.ar.bits.size
//     arburst := arbiter.Arbiter.ar.bits.burst
//     arlock := arbiter.Arbiter.ar.bits.lock
//     arcache := arbiter.Arbiter.ar.bits.cache
//     arprot := arbiter.Arbiter.ar.bits.prot
//     arvalid := arbiter.Arbiter.ar.valid
//     arbiter.Arbiter.ar.ready := arready
//     // Read back
//     arbiter.Arbiter.r.bits.id := rid
//     arbiter.Arbiter.r.bits.data := rdata
//     arbiter.Arbiter.r.bits.resp := rresp
//     arbiter.Arbiter.r.bits.last := rlast
//     arbiter.Arbiter.r.valid := rvalid
//     rready := arbiter.Arbiter.r.ready
//     arbiter.Arbiter.r.bits.user := 0.U
//     // Write request
//     awid := arbiter.Arbiter.aw.bits.id
//     awaddr := arbiter.Arbiter.aw.bits.addr
//     awlen := arbiter.Arbiter.aw.bits.len
//     awsize := arbiter.Arbiter.aw.bits.size
//     awburst := arbiter.Arbiter.aw.bits.burst
//     awlock := arbiter.Arbiter.aw.bits.lock
//     awcache := arbiter.Arbiter.aw.bits.cache
//     awprot := arbiter.Arbiter.aw.bits.prot
//     awvalid := arbiter.Arbiter.aw.valid
//     arbiter.Arbiter.aw.ready := awready
//     // Write data
//     wid := arbiter.Arbiter.w.bits.id
//     wdata := arbiter.Arbiter.w.bits.data
//     wstrb := arbiter.Arbiter.w.bits.strb
//     wlast := arbiter.Arbiter.w.bits.last
//     wvalid := arbiter.Arbiter.w.valid
//     arbiter.Arbiter.w.ready := wready
//     // Write back
//     arbiter.Arbiter.b.bits.id := bid
//     arbiter.Arbiter.b.bits.resp := bresp
//     arbiter.Arbiter.b.valid := bvalid
//     bready := arbiter.Arbiter.b.ready
//     arbiter.Arbiter.b.bits.user := 0.U
//
//     debug0_wb_pc := RegNext(
//       Mux(
//         core.io.difftest_redirect.valid,
//         core.io.difftest_redirect.target,
//         core.io.difftest_commit.bits.decode.cf.pnpc
//       )
//     )
//     debug0_wb_rf_wdata := 0.U
//     debug0_wb_rf_wnum := 0.U
//     debug0_wb_rf_wen := 0.U
//
//     core.io.difftest_commit.ready := true.B
//
//   }
//
// //	io.commit := RegNext(core.io.difftest_commit.valid)
// //	io.instr := RegNext(core.io.difftest_instr)
// //	io.pc := RegNext(Mux(core.io.difftest_redirect.valid,
// //		core.io.difftest_redirect.target,
// //		core.io.difftest_commit.bits.decode.cf.pnpc))
// }
