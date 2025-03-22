package blackbox.ip

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.axi4._

class AXI2APB_MISC extends BlackBox(Map(
	"Lawaddr"	-> AXI4Parameters.addrBits,
	"Lawlen"	-> AXI4Parameters.lenBits,
	"Lawsize"	-> AXI4Parameters.sizeBits,
	"Lawburst"	-> AXI4Parameters.burstBits,
	"Lawlock"	-> 1, // Bool in axi4
	"Lawcache"	-> AXI4Parameters.cacheBits,
	"Lawprot"	-> AXI4Parameters.protBits,
	"Lwdata"	-> AXI4Parameters.dataBits,
	"Lwstrb"	-> AXI4Parameters.dataBits/8,
	"Lbresp"	-> AXI4Parameters.respBits,
	"Laraddr"	-> AXI4Parameters.addrBits,
	"Larlen"	-> AXI4Parameters.lenBits,
	"Larsize"	-> AXI4Parameters.sizeBits,
	"Larburst"	-> AXI4Parameters.burstBits,
	"Larlock"	-> 1, // Bool in axi4
	"Larcache"	-> AXI4Parameters.cacheBits,
	"Larprot"	-> AXI4Parameters.protBits,
	"Lrdata"	-> AXI4Parameters.dataBits,
	"Lrresp"	-> AXI4Parameters.respBits,
	// Some thing
	"LID"		-> AXI4Parameters.idBits
)){
  def ADDR_APB = 20
  def DATA_APB = 8
  
	val io = IO(new Bundle {
		val clk = Input(Clock())
		val rst_n = Input(Bool())
		val axi_s_awid = Input(UInt(AXI4Parameters.idBits.W))
		val axi_s_awaddr = Input(UInt(AXI4Parameters.addrBits.W))
		val axi_s_awlen = Input(UInt(AXI4Parameters.lenBits.W))
		val axi_s_awsize = Input(UInt(AXI4Parameters.sizeBits.W))
		val axi_s_awburst = Input(UInt(AXI4Parameters.burstBits.W))
		val axi_s_awlock = Input(UInt(1.W))
		val axi_s_awcache = Input(UInt(AXI4Parameters.cacheBits.W))
		val axi_s_awport = Input(UInt(AXI4Parameters.protBits.W))
		val axi_s_awvalid = Input(UInt(1.W))
		val axi_s_awready = Output(UInt(1.W))
		val axi_s_wid = Input(UInt(AXI4Parameters.idBits.W))
		val axi_s_wdata = Input(UInt(AXI4Parameters.dataBits.W))
		val axi_s_wstrb = Input(UInt((AXI4Parameters.dataBits/8).W))
		val axi_s_wlast = Input(UInt(1.W))
		val axi_s_wvalid = Input(UInt(1.W))
		val axi_s_wready = Output(UInt(1.W))
		val axi_s_bid = Output(UInt(AXI4Parameters.idBits.W))
		val axi_s_bresp = Output(UInt(AXI4Parameters.respBits.W))
		val axi_s_bvalid = Output(UInt(1.W))
		val axi_s_bready = Input(UInt(1.W))
		val axi_s_arid = Input(UInt(AXI4Parameters.lenBits.W))
		val axi_s_araddr = Input(UInt(AXI4Parameters.addrBits.W))
		val axi_s_arlen = Input(UInt(AXI4Parameters.lenBits.W))
		val axi_s_arsize = Input(UInt(AXI4Parameters.sizeBits.W))
		val axi_s_arburst = Input(UInt(AXI4Parameters.burstBits.W))
		val axi_s_arlock = Input(UInt(1.W))
		val axi_s_arcache = Input(UInt(AXI4Parameters.cacheBits.W))
		val axi_s_arprot = Input(UInt(AXI4Parameters.protBits.W))
		val axi_s_arvalid = Input(UInt(1.W))
		val axi_s_arready = Output(UInt(1.W))
		val axi_s_rid = Output(UInt(AXI4Parameters.idBits.W))
		val axi_s_rdata = Output(UInt(AXI4Parameters.dataBits.W))
		val axi_s_rresp = Output(UInt(AXI4Parameters.respBits.W))
		val axi_s_rlast = Output(UInt(1.W))
		val axi_s_rvalid = Output(UInt(1.W))
		val axi_s_rready = Input(UInt(1.W))

		val apb_ready_dma = Output(UInt(1.W))
		val apb_rw_dma = Input(UInt(1.W))
		val apb_psel_dma = Input(UInt(1.W))
		val apb_enab_dma = Input(UInt(1.W))
		val apb_addr_dma = Input(UInt(ADDR_APB.W))
		val apb_wdata_dma = Input(UInt(32.W))
		val apb_rdata_dma = Output(UInt(32.W))
		val apb_valid_dma = Input(UInt(1.W))
		val dma_grant = Output(UInt(1.W))

		val dma_req_o = Output(UInt(1.W))
		val dma_ack_i = Input(UInt(1.W))

		val uart0_txd_i = Input(UInt(1.W))
		val uart0_txd_o = Output(UInt(1.W))
		val uart0_txd_oe = Output(UInt(1.W))
		val uart0_rxd_i = Input(UInt(1.W))
		val uart0_rxd_o = Output(UInt(1.W))
		val uart0_rxd_oe = Output(UInt(1.W))
		val uart0_rts_o = Output(UInt(1.W))
		val uart0_dtr_o = Output(UInt(1.W))
		val uart0_cts_i = Input(UInt(1.W))
		val uart0_dsr_i = Input(UInt(1.W))
		val uart0_dcd_i = Input(UInt(1.W))
		val uart0_ri_i = Input(UInt(1.W))

		val uart0_int = Output(UInt(1.W))
		val nand_int = Output(UInt(1.W))

		val nand_type = Input(UInt(2.W))
		val nand_cle = Output(UInt(1.W))
		val nand_ale = Output(UInt(1.W))
		val nand_rdy = Input(UInt(4.W))
		val nand_rd = Output(UInt(1.W))
		val nand_ce = Output(UInt(4.W))
		val nand_wr = Output(UInt(1.W))
		val nand_dat_i = Input(UInt(8.W))
		val nand_dat_o = Output(UInt(8.W))
		val nand_dat_oe = Output(UInt(1.W))
	})
}
