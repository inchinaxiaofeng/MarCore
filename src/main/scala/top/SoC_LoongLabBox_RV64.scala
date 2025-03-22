package top

import chisel3._
import chisel3.util._
import chisel3.experimental._

import top._
import defs._
import device._
import bus.axi4._

class SoC_LoongLabBox extends Module with SoC_LoongLabBox_ConstReq with General_ConstReq_RV64 {
	/* GPIO */
	val led = IO(new Bundle {
		val led = Output(UInt(16.W))
		val rg0	= Output(UInt(2.W))
		val rg1	= Output(UInt(2.W))
	})
	val swith = IO(Input(UInt(8.W)))
	val num = IO(new Bundle {
		val csn = Output(UInt(8.W))
		val a_g = Output(UInt(7.W))
	})
	val btn = IO(new Bundle {
		val key_col = Output(UInt(4.W))
		val key_row = Input(UInt(4.W))
		val step = Input(UInt(2.W))
	})
	/* si exec */
	val btn_clk = IO(Input(UInt(1.W)))
	/* lcd 触摸屏的相关接口 */
	val lcd = IO(new Bundle {
		val rst = Output(UInt(1.W))
		val cs = Output(UInt(1.W))
		val rs = Output(UInt(1.W))
		val wr = Output(UInt(1.W))
		val rd = Output(UInt(1.W))
		val data_io = Analog(16.W)
		val bl_ctr = Output(UInt(1.W))
	})
	val ct = IO(new Bundle {
		val int = Analog(1.W)
		val sda = Analog(1.W)
		val scl = Output(UInt(1.W))
		val rstn = Output(UInt(1.W))
	})
	/* DDR3 interface */
	val ddr3 = IO(new Bundle {
		val dq		= Analog(16.W)
		val addr	= Output(UInt(13.W))
		val ba		= Output(UInt(3.W))
		val ras_n	= Output(UInt(1.W))
		val cas_n	= Output(UInt(1.W))
		val we_n	= Output(UInt(1.W))
		val odt		= Output(UInt(1.W))
		val reset_n	= Output(UInt(1.W))
		val cke		= Output(UInt(1.W))
		val dm		= Output(UInt(2.W))
		val dqs_p	= Analog(2.W)
		val dqs_n	= Analog(2.W)
		val ck_p	= Output(UInt(1.W))
		val cn_n	= Output(UInt(1.W))
	})
	/* mac controller */
	// TX
	val mtx = IO(new Bundle {
		val ckl_0 = Input(UInt(1.W))
		val en_0 = Output(UInt(1.W))
		val d_0 = Output(UInt(4.W))
		val err_0 = Output(UInt(1.W))
	})
	// RX
	val mrx = IO(new Bundle {
		val clk_0 = Input(UInt(1.W))
		val dv_0 = Input(UInt(1.W))
		val d_0 = Input(UInt(4.W))
		val err_0 = Input(UInt(1.W))
	})
	val mc = IO(new Bundle {
		val oll_0 = Input(UInt(1.W))
		val rs_0 = Input(UInt(1.W))
	})
	// MIIM
	val md = IO(new Bundle {
		val c_0 = Output(UInt(1.W))
		val io_0 = Analog(1.W)
	})
	//
	val phy_rstn = Output(UInt(1.W))
	/* EJTAG */
	val EJTAG = IO(new Bundle {
		val TRST = Input(UInt(1.W))
		val TCK = Input(UInt(1.W))
		val TDI = Input(UInt(1.W))
		val TMS = Input(UInt(1.W))
		val TDO = Output(UInt(1.W))
	})
	/* uart */
	val UART = IO(new Bundle {
		val RX = Analog(1.W)
		val TX = Analog(1.W)
	})
	/* nand */
	val NAND = IO(new Bundle {
		val CLE = Output(UInt(1.W))
		val ALE = Output(UInt(1.W))
		val RDY = Input(UInt(1.W))
		val DATA = Analog(8.W)
		val RD = Output(UInt(1.W))
		val CE = Output(UInt(1.W)) // low active
		val WR = Output(UInt(1.W))
	})
	/* spi flash */
	val SPI = IO(new Bundle {
		val CLK = Output(UInt(1.W))
		val CS = Output(UInt(1.W))
		val MISO = Analog(1.W)
		val MOSI = Analog(1.W)
	})
	/* VGA */
	val VGA = IO(new Bundle {
		val R = Output(UInt(4.W))
		val G = Output(UInt(4.W))
		val B = Output(UInt(4.W))
		val VSYNC = Output(UInt(1.W))
		val HSYNC = Output(UInt(1.W))
	})
	/* USB */
	lazy val config = MarCoreConfig(FPGAPlatform = true)
	val core = Module(new Core()(config))

	/* nand controller */
//	val axi2apb_misc = Module(new AXI2APB_MISC)
//	axi2apb_misc.nand_type := 2.U
//	NAND.CLE := axi2apb_misc.nand_cle
//	NAND.ALE := axi2apb_misc.nand_ale
//	axi2apb_misc.nand_rdy := Cat(0.U(3.W), NAND.RDY)
//	// TODO: IOBUF
//	NAND.RD := axi2apb_misc.nand_rd
//	NAND.CE := axi2apb_misc.nand_ce(0) // low active
//	NAND.WR := axi2apb_misc.nand_wr
	
	// TODO: UART controller

//	val display_valid = RegNext(next, 0.U)
//	val display_name = RegNext(next, 0.U(40.W))
//	val display_value = RegNext(next, 0.U(32.W))
	// TODO: LCD (DCP文件)
}
