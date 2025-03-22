//package top.vivado
//
//import chisel3._
//import chisel3.util._
//
//import top.chiplab._
//
//class thinpad extends RawModule {
//	lazy val config = MarCoreConfig(FPGAPlatform = false)
//	implicit val moduleName: String = this.name
//	val clk_50M		= IO(Input(Clock())) // 50MHz 时钟输入
//	val clk_11M0592	= IO(Input(Clock())) // 11.0592MHz 时钟输入（备用，可不用）
//	val clock_btn	= IO(Input(Clock())) // BTN5手动时钟按钮开关，带消抖电路，按下时为1
//	val reset_btn	= IO(Input(Bool())) // BTN6手动复位按钮开关，带消抖电路，按下时为1
//
//	// BaseRAM Signal
//	val base_ram_data_o	= IO(Output(UInt(32.W))) // BaseRAM数据，低8位与CPLD串口控制器共享
//	val base_ram_data_i	= IO(Input(UInt(32.W))) // BaseRAM数据，低8位与CPLD串口控制器共享
//	val base_ram_addr	= IO(Output(UInt(20.W))) // BaseRAM地址
//	val base_ram_be_n	= IO(Output(UInt(4.W))) // BaseRAM字节使能，低有效。如果不使用字节使能，请保持为0
//	val base_ram_ce_n	= IO(Output(UInt(1.W))) // BaseRAM片选，低有效
//	val base_ram_oe_n	= IO(Output(UInt(1.W))) // aseRAM读使能，低有效
//	val base_ram_we_n	= IO(Output(UInt(1.W))) // BaseRAM写使能，低有效
//
//	val core = Module(new core_top()(config))
//	core.aclk := clk_50M
//	core.aresetn := !reset_btn
//
//	val addr = Wire(UInt(32.W))
//	when (arvalid) {
//		addr := araddr
//	}.elsewhen (awvalid) {
//		addr := awaddr
//	}
//	base_ram_addr := addr(18, 0)
//	base_ram_ce_n := !addr(19)
//
//	val data_out = Wire(UInt(32.W))
//	when (wvalid) {
//		data_out := wdata
//	}
//	base_ram_data_o := data_out
//
//	val data_in = base_ram_data_i
//	core.rid := 0.U
//	core.rdata := data_in
////	core.rresp := 
//	core.
//}
