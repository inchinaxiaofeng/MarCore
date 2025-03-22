package blackbox

import chisel3._
import chisel3.util._

import defs._
import utils._
import top.Settings

// Real top for ChipLab
class core_top extends BlackBox {
	val io = IO(new Bundle {
		val aclk	= Input(Clock())
		val aresetn	= Input(Bool())
		val intrpt	= Input(UInt(8.W))
		// AXI Interface
		// Read request
		val arid	= Output(UInt(4.W))
		val araddr	= Output(UInt(32.W))
		val arlen	= Output(UInt(8.W))
		val arsize	= Output(UInt(3.W))
		val arburst	= Output(UInt(2.W))
		val arlock	= Output(UInt(2.W))
		val arcache	= Output(UInt(4.W))
		val arprot	= Output(UInt(3.W))
		val arvalid	= Output(UInt(1.W))
		val arready	= Input(UInt(1.W))
		// Read back
		val rid		= Input(UInt(4.W))
		val rdata	= Input(UInt(32.W))
		val rresp	= Input(UInt(2.W))
		val rlast	= Input(UInt(1.W))
		val rvalid	= Input(UInt(1.W))
		val rready	= Output(UInt(1.W))
		// Write request
		val awid	= Output(UInt(4.W))
		val awaddr	= Output(UInt(32.W))
		val awlen	= Output(UInt(8.W))
		val awsize	= Output(UInt(3.W))
		val awburst	= Output(UInt(2.W))
		val awlock	= Output(UInt(2.W))
		val awcache	= Output(UInt(4.W))
		val awprot	= Output(UInt(3.W))
		val awvalid	= Output(UInt(1.W))
		val awready	= Input(UInt(1.W))
		// Write data
		val wid		= Output(UInt(4.W))
		val wdata	= Output(UInt(32.W))
		val wstrb	= Output(UInt(4.W))
		val wlast	= Output(UInt(1.W))
		val wvalid	= Output(UInt(1.W))
		val wready	= Input(UInt(1.W))
		// Write back
		val bid		= Input(UInt(4.W))
		val bresp	= Input(UInt(2.W))
		val bvalid	= Input(UInt(1.W))
		val bready	= Output(UInt(1.W))
		// NOTE: Debug Info
		val debug0_wb_pc		= Output(UInt(32.W))
		val debug0_wb_rf_wen	= Output(UInt(4.W))
		val debug0_wb_rf_wnum	= Output(UInt(5.W))
		val debug0_wb_rf_wdata	= Output(UInt(32.W))
		if (Settings.get("CPU_2CMT")) {
			val debug1_wb_pc		= Output(UInt(32.W))
			val debug1_wb_rf_wen	= Output(UInt(4.W))
			val debug1_wb_rf_wnum	= Output(UInt(5.W))
			val debug1_wb_rf_wdata	= Output(UInt(32.W))
		}
	})
}
