package top.sta

import chisel3._
import chisel3.util._

import top._
import bus.cacheBus._
import defs._
import utils._
import bus.axi4._

class STA_Core extends RawModule with STACore_ConstReq {
  val clk = IO(Input(Clock()))
  val rst_n = IO(Input(Bool()))
  val ram = IO(new SimTopIO)

  lazy val config = MarCoreConfig(FPGAPlatform = false)
  val core = withClockAndReset(clk, !rst_n) { Module(new Core()(config)) }
  val arbiter = withClockAndReset(clk, !rst_n) { Module(new AXI4_Arbiter_MMIO) }
  val bridge = Module(new soc_axi_sram_bridge())
  withClockAndReset(clk, !rst_n) {
    core.io.imem.toAXI4(isFromCache = true)   <> arbiter.InstFetch
    core.io.dmem.toAXI4(isFromCache = true)   <> arbiter.LoadStore
    core.io.mmio.toAXI4(isFromCache = false)  <> arbiter.MMIO
  }

	// clk n reset
	bridge.io.aclk := clk
	bridge.io.aresetn := rst_n
	// RAM
	ram.raddr	:= bridge.io.ram_raddr
	bridge.io.ram_rdata := ram.rdata
	ram.ren		:= bridge.io.ram_ren
	ram.waddr	:= bridge.io.ram_waddr
	ram.wdata	:= bridge.io.ram_wdata
	ram.wen		:= bridge.io.ram_wen
	// AR
	bridge.io.m_araddr	:= arbiter.Arbiter.ar.bits.addr
	bridge.io.m_arburst	:= arbiter.Arbiter.ar.bits.burst
	bridge.io.m_arcache	:= arbiter.Arbiter.ar.bits.cache
	bridge.io.m_arid	:= arbiter.Arbiter.ar.bits.id
	bridge.io.m_arlen	:= arbiter.Arbiter.ar.bits.len(3,0)
	bridge.io.m_arlock	:= arbiter.Arbiter.ar.bits.lock
	bridge.io.m_arprot	:= arbiter.Arbiter.ar.bits.prot
	arbiter.Arbiter.ar.ready := bridge.io.m_arready
	bridge.io.m_arsize	:= arbiter.Arbiter.ar.bits.size
	bridge.io.m_arvalid	:= arbiter.Arbiter.ar.valid
	// AW
	bridge.io.m_awaddr	:= arbiter.Arbiter.aw.bits.addr
	bridge.io.m_awburst	:= arbiter.Arbiter.aw.bits.burst
	bridge.io.m_awcache	:= arbiter.Arbiter.aw.bits.cache
	bridge.io.m_awid	:= arbiter.Arbiter.aw.bits.id
	bridge.io.m_awlen	:= arbiter.Arbiter.aw.bits.len(3,0)
	bridge.io.m_awlock	:= arbiter.Arbiter.aw.bits.lock
	bridge.io.m_awprot	:= arbiter.Arbiter.aw.bits.prot
	arbiter.Arbiter.aw.ready := bridge.io.m_awready
	bridge.io.m_awsize	:= arbiter.Arbiter.aw.bits.size
	bridge.io.m_awvalid	:= arbiter.Arbiter.aw.valid
	// B
	arbiter.Arbiter.b.bits.id	:= bridge.io.m_bid
	bridge.io.m_bready := arbiter.Arbiter.b.ready
	arbiter.Arbiter.b.bits.resp	:= bridge.io.m_bresp
	arbiter.Arbiter.b.valid		:= bridge.io.m_bvalid
	arbiter.Arbiter.b.bits.user	:= 0.U
	// R
	arbiter.Arbiter.r.bits.data	:= bridge.io.m_rdata
	arbiter.Arbiter.r.bits.id	:= bridge.io.m_rid
	arbiter.Arbiter.r.bits.last	:= bridge.io.m_rlast
	bridge.io.m_rready := arbiter.Arbiter.r.ready
	arbiter.Arbiter.r.bits.resp	:= bridge.io.m_rresp
	arbiter.Arbiter.r.valid		:= bridge.io.m_rvalid
	arbiter.Arbiter.r.bits.user	:= 0.U
	// W
	bridge.io.m_wdata	:= arbiter.Arbiter.w.bits.data
	bridge.io.m_wid		:= arbiter.Arbiter.w.bits.id
	bridge.io.m_wlast	:= arbiter.Arbiter.w.bits.last
	arbiter.Arbiter.w.ready := bridge.io.m_wready
	bridge.io.m_wstrb	:= arbiter.Arbiter.w.bits.strb
	bridge.io.m_wvalid	:= arbiter.Arbiter.w.valid


}
