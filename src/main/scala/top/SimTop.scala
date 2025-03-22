package top

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import bus.axi4.{AXI4, AXI4Lite, AXI4_Arbiter_MMIO}
import module._
import defs._
import utils._
import top.Settings
import bus.cacheBus._
import bus.debugBus.{DebugBus}

class SimpleIO extends Bundle {
	val commit = Output(Bool())
	val pc = Output(UInt(64.W))
	val instr = Output(UInt(64.W))
	val gpr = new RegsDiffIO(num = 32)
	val csr = new RegsDiffIO(num = 4)
	val debugBus = Flipped(new DebugBus)
}

class soc_axi_sram_bridge extends BlackBox(Map(
	"BUS_WIDTH"		-> 32,
	"DATA_WIDTH"	-> 64,
	"CPU_WIDTH"		-> 32)) {
	val io = IO(new Bundle {
		val aclk	= Input(Clock())
		val aresetn	= Input(Bool())
		// SOC
		val ram_raddr	= Output(UInt(32.W))
		val ram_rdata	= Input(UInt(64.W))
		val ram_ren		= Output(Bool())
		val ram_waddr	= Output(UInt(32.W))
		val ram_wdata	= Output(UInt(64.W))
		val ram_wen		= Output(UInt(8.W))
		// AXI
		// AR
		val m_araddr	= Input(UInt(32.W))
		val m_arburst	= Input(UInt(2.W))
		val m_arcache	= Input(UInt(4.W))
		val m_arid		= Input(UInt(4.W))
		val m_arlen		= Input(UInt(4.W))
		val m_arlock	= Input(UInt(2.W))
		val m_arprot	= Input(UInt(3.W))
		val m_arready	= Output(Bool())
		val m_arsize	= Input(UInt(3.W))
		val m_arvalid	= Input(Bool())
		// AW
		val m_awaddr	= Input(UInt(32.W))
		val m_awburst	= Input(UInt(2.W))
		val m_awcache	= Input(UInt(4.W))
		val m_awid		= Input(UInt(4.W))
		val m_awlen		= Input(UInt(4.W))
		val m_awlock	= Input(UInt(2.W))
		val m_awprot	= Input(UInt(3.W))
		val m_awready	= Output(Bool())
		val m_awsize	= Input(UInt(3.W))
		val m_awvalid	= Input(Bool())
		// B
		val m_bid		= Output(UInt(4.W))
		val m_bready	= Input(Bool())
		val m_bresp		= Output(UInt(2.W))
		val m_bvalid	= Output(Bool())
		// R
		val m_rdata		= Output(UInt(64.W))
		val m_rid		= Output(UInt(4.W))
		val m_rlast		= Output(Bool())
		val m_rready	= Input(Bool())
		val m_rresp		= Output(UInt(2.W))
		val m_rvalid	= Output(Bool())
		// W
		val m_wdata		= Input(UInt(64.W))
		val m_wid		= Input(UInt(4.W))
		val m_wlast		= Input(Bool())
		val m_wready	= Output(Bool())
		val m_wstrb		= Input(UInt(8.W))
		val m_wvalid	= Input(Bool())
	})
}

class SimTopIO extends Bundle {
	val raddr	= Output(UInt(32.W))
	val rdata	= Input(UInt(64.W))
	val ren		= Output(Bool())
	val waddr	= Output(UInt(32.W))
	val wdata	= Output(UInt(64.W))
	val wen		= Output(UInt(8.W))
}

class SimTop extends Module with Sim_ConstReq with General_ConstReq_RV64 {
	lazy val config = MarCoreConfig(FPGAPlatform = false)
	implicit val moduleName: String = this.name
	val io = IO(new SimpleIO())
	val ram = IO(new SimTopIO)
	val bridge = Module(new soc_axi_sram_bridge())
	val core = Module(new Core()(config))
	val arbiter = Module(new AXI4_Arbiter_MMIO)

	io.debugBus := DontCare//<> core.io.debugBus.get

	if (Settings.get("DiffTestGPR") && Settings.get("EnableDifftest")) {
		for (i <- 0 until 32) io.gpr.regs(i) := core.io.gpr.get.regs(i)
	} else {
		for (i <- 0 until 32) io.gpr.regs(i) := 0.U(64.W)
	}

	if (Settings.get("DiffTestCSR") && Settings.get("EnableDifftest")) {
		for (i <- 0 until 4) io.csr.regs(i) := core.io.csr.get.regs(i)
	} else {
		for (i <- 0 until 4) io.csr.regs(i) := 0.U(64.W)
	}

	if (Settings.get("EnableDifftest")) {
		core.io.difftest_commit.get.ready := true.B
		io.commit := RegNext(core.io.difftest_commit.get.valid)
		io.instr := RegNext(core.io.difftest_instr.get)
		io.pc := RegNext(Mux(core.io.difftest_redirect.get.valid,
			core.io.difftest_redirect.get.target,
			core.io.difftest_commit.get.bits.decode.cf.pnpc))
	} else {
    io.commit := 0.U
    io.instr := 0.U
    io.pc := 0.U
  }

	core.io.imem.toAXI4(isFromCache = true)		<> arbiter.InstFetch
	core.io.dmem.toAXI4(isFromCache = true)		<> arbiter.LoadStore
	core.io.mmio.toAXI4(isFromCache = false)	<> arbiter.MMIO

	// clk n reset
	bridge.io.aclk := clock
	bridge.io.aresetn := !reset.asBool
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

	Debug("[RAM] raddr %x rdata %x ren %d waddr %x wdata %x wen %b\n",
		bridge.io.ram_raddr, bridge.io.ram_rdata, bridge.io.ram_ren,
		bridge.io.ram_waddr, bridge.io.ram_wdata, bridge.io.ram_wen)
//	Debug(arbiter.Arbiter.r.valid, "[SOC RESP]\n")
}
