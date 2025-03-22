package blackbox.ip

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.axi4._

class dma_master extends BlackBox(Map(
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
)) {
	val io = IO(new Bundle {
		val clk		= Input(Clock())
		val rst_n	= Input(Bool())

		val arid	= Output(UInt(AXI4Parameters.lenBits.W))
		val araddr	= Output(UInt(AXI4Parameters.addrBits.W))
		val arlen	= Output(UInt(AXI4Parameters.lenBits.W))
		val arsize	= Output(UInt(AXI4Parameters.sizeBits.W))
		val arburst	= Output(UInt(AXI4Parameters.burstBits.W))
		val arlock	= Output(UInt(1.W))
		val arcache	= Output(UInt(AXI4Parameters.cacheBits.W))
		val arprot	= Output(UInt(AXI4Parameters.protBits.W))
		val arvalid	= Output(UInt(1.W))
		val arready	= Input(UInt(1.W))

		val rid		= Input(UInt(AXI4Parameters.idBits.W))
		val rdata	= Input(UInt(AXI4Parameters.dataBits.W))
		val rresp	= Input(UInt(AXI4Parameters.respBits.W))
		val rlast	= Input(UInt(1.W))
		val rvalid	= Input(UInt(1.W))
		val rready	= Output(UInt(1.W))

		val awid	= Output(UInt(AXI4Parameters.idBits.W))
		val awaddr	= Output(UInt(AXI4Parameters.addrBits.W))
		val awlen	= Output(UInt(AXI4Parameters.lenBits.W))
		val awsize	= Output(UInt(AXI4Parameters.sizeBits.W))
		val awburst	= Output(UInt(AXI4Parameters.burstBits.W))
		val awlock	= Output(UInt(1.W))
		val awcache	= Output(UInt(AXI4Parameters.cacheBits.W))
		val awprot	= Output(UInt(AXI4Parameters.protBits.W))
		val awvalid	= Output(UInt(1.W))
		val awready	= Input(UInt(1.W))

		val wid		= Output(UInt(AXI4Parameters.idBits.W))
		val wdata	= Output(UInt(AXI4Parameters.dataBits.W))
		val wstrb	= Output(UInt((AXI4Parameters.dataBits/8).W))
		val wlast	= Output(UInt(1.W))
		val wvalid	= Output(UInt(1.W))
		val wready	= Input(UInt(1.W))

		val bid		= Input(UInt(AXI4Parameters.idBits.W))
		val bresp	= Input(UInt(AXI4Parameters.respBits.W))
		val bvalid	= Input(UInt(1.W))
    val bready	= Output(UInt(1.W))

		val dma_int = Output(UInt(1.W))
		val order_addr_in = Input(UInt(32.W)) // FIXME:
		val dma_req_in = Input(UInt(1.W))
		val dma_ack_out = Output(UInt(1.W))
		val finish_read_order = Output(UInt(1.W))
		val write_dma_end = Output(UInt(1.W))
		val dma_gnt = Input(UInt(1.W))
		
		val apb_valid_req = Output(UInt(1.W))
		val apb_psel = Output(UInt(1.W))
		val apb_penable = Output(UInt(1.W))
		val apb_rw = Output(UInt(1.W))
		val apb_addr = Output(UInt(32.W))
		val apb_rdata = Input(UInt(32.W))
		val apb_wdata = Output(UInt(32.W))
	})
}
