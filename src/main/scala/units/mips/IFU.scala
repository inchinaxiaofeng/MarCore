package units.mips

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import bus.cacheBus._
import module.fu._
import defs._
import utils._
import top.Settings

trait HasResetVector {
	val resetVector = Settings.getLong("ResetVector")
}

class IFU_embedded extends MarCoreModule with HasResetVector {
	implicit val moduleName: String = this.name
	val io = IO(new Bundle {
		val imem = new CacheBus(addrBits = VAddrBits, userBits = ICacheUserBundleWidth)
		val out = Decoupled(new CtrlFlowIO_MIPS)
		val redirect = Flipped(new RedirectIO)
		val flushVec = Output(UInt(4.W))
		val bpuUpdate = Flipped(new BPUUpdate)
	})

	val pc = RegInit(resetVector.U(VAddrBits.W))
	val pcUpdate = io.redirect.valid || io.imem.req.fire
	val bpu = Module(new BPU_embedded)
	val snpc = pc + 4.U
	val pnpc = snpc//bpu.io.out.target // predict next pc
	val npc = Mux(io.redirect.valid, io.redirect.target, Mux(bpu.io.out.valid, pnpc, snpc))

	bpu.io.in.pc.valid := io.imem.req.fire // only predict when ICache accepts a request
	bpu.io.in.pc.bits := npc // predict one cycle early
	bpu.io.flush := io.redirect.valid
	bpu.io.bpuUpdate <> io.bpuUpdate

	when (pcUpdate) { pc := npc }

	io.flushVec := Mux(io.redirect.valid, "b1111".U, 0.U)

	io.imem := DontCare
	io.imem.req.bits.apply(
		addr = pc,
		len = 1.U,
		size = "b10".U,
		last = true.B,
		write = false.B,
		user = Cat(pc, npc)
	)
	io.imem.req.valid := io.out.ready
	io.imem.resp.ready := io.out.ready || io.flushVec(0)

	io.out.bits := DontCare
	io.out.bits.instr := io.imem.resp.bits.data

	io.imem.resp.bits.user.map { case x =>
		io.out.bits.pc := x(2*VAddrBits-1, VAddrBits)
		io.out.bits.pnpc := x(VAddrBits-1, 0)
	}
	io.out.valid := io.imem.resp.valid && !io.flushVec(0)

//	Debug(io.imem.req.fire, magentaFG+"[IFI]"+resetColor+" pc=%x redirect %x npc %x pc %x pnpc %x\n", io.imem.req.bits.addr, io.redirect.valid, npc, pc, bpu.io.out.target)
//	Debug(io.out.fire, magentaFG+"[IFO]"+resetColor+" pc=%x inst=%x npc=%x ipf %x\n", io.out.bits.pc, io.out.bits.instr, io.out.bits.pnpc, io.ipf)
//	Debug(io.redirect.valid, magentaFG+"[Redirect]"+resetColor+" target 0x%x rtype %b\n", io.redirect.target, io.redirect.rtype)
}
