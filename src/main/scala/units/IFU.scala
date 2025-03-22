package units

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

class IFU_inorder extends MarCoreModule with HasResetVector {
	implicit val moduleName: String = this.name
	val io = IO(new Bundle {
		val imem = new CacheBus(addrBits = VAddrBits, userBits = ICacheUserBundleWidth)
		val out = Decoupled(new CtrlFlowIO)

		val redirect = Flipped(new RedirectIO)
		val flushVec = Output(UInt(4.W))
		val bpFlush = Output(Bool())
		val ipf = Input(Bool())
		val bpuUpdate = Flipped(new BPUUpdate)
	})

	val pc = RegInit(resetVector.U(VAddrBits.W))
	val pcUpdate = io.redirect.valid || io.imem.req.fire
	val snpc = Mux(pc(1), pc + 2.U, pc + 4.U) /// sequential next pc
	val bpu = Module(new BPU_inorder)

	val crosslineJump = bpu.io.crosslineJump
	val crosslineJumpLatch = RegInit(false.B)
	when (pcUpdate || bpu.io.flush) {
		crosslineJumpLatch := Mux(bpu.io.flush, false.B, crosslineJump && !crosslineJumpLatch)
	}
	val crosslineJumpTarget = RegEnable(bpu.io.out.target, crosslineJump)
	val crosslineJumpForceSeq = crosslineJump && bpu.io.out.valid
	val crosslineJumpForceTgt = crosslineJumpLatch && !bpu.io.flush

	// predicted next pc
	val pnpc = Mux(crosslineJump, snpc, bpu.io.out.target)
	val pbrIdx = bpu.io.brIdx
	val npc = Mux(io.redirect.valid, io.redirect.target, Mux(crosslineJumpLatch, crosslineJumpTarget, Mux(bpu.io.out.valid, pnpc, snpc)))
	val npcIsSeq = Mux(io.redirect.valid, false.B, Mux(crosslineJumpLatch, false.B, Mux(crosslineJump, true.B, Mux(bpu.io.out.valid, false.B, true.B))))
	Debug("[NPC] %x %x %x %x %x %x\n", crosslineJumpLatch, crosslineJumpTarget, crosslineJump, bpu.io.out.valid, pnpc, snpc)

	// CHEKCK <green>
	val brIdx = Wire(UInt(4.W))
	brIdx := Cat(npcIsSeq, Mux(io.redirect.valid, 0.U, pbrIdx))
	bpu.io.in.pc.valid := io.imem.req.fire // only predict when Icache accepts a request
	bpu.io.in.pc.bits := npc // predict one cycle early
	bpu.io.flush := io.redirect.valid
	bpu.io.bpuUpdate <> io.bpuUpdate

	when (pcUpdate) { pc := npc }

	io.flushVec := Mux(io.redirect.valid, "b1111".U, 0.U)
	io.bpFlush := false.B

	io.imem := DontCare
	io.imem.req.bits.apply(
		addr = pc,
		len = 1.U,
		size = "b10".U,
		last = true.B,
		write = false.B,
		user = Cat(brIdx(3,0), pc(VAddrBits-1, 0), npc(VAddrBits-1, 0))
		)
	io.imem.req.valid := io.out.ready
	io.imem.req.ready := io.out.ready || io.flushVec(0)

	io.out.bits := DontCare

	io.out.bits.instr := io.imem.resp.bits.data
	io.imem.resp.bits.user.map{ case x =>
		io.out.bits.brIdx := x(VAddrBits*2+3, VAddrBits*2)
		io.out.bits.pc := x(2*VAddrBits-1, VAddrBits)
		io.out.bits.pnpc := x(VAddrBits-1, 0)
	}
	io.out.bits.exceptionVec(instrPageFault) := io.ipf
	io.out.valid := io.imem.resp.valid && !io.flushVec(0)
	// <\green>

	Debug(io.imem.req.fire, "[IFI] pc %x redirect %x npc %x pc %x pnpc %x\n", io.imem.req.bits.addr, io.redirect.valid, npc, pc, bpu.io.out.target)
	Debug(io.out.fire, "[IFO] pc %x inst %x npc %x\n", io.out.bits.pc, io.out.bits.instr, io.out.bits.pnpc, io.ipf)
	Debug(io.redirect.valid, "[Redirect] target %x rtype %b\n", io.redirect.target, io.redirect.rtype)
}

class IFU_embedded extends MarCoreModule with HasResetVector {
	implicit val moduleName: String = this.name
	val io = IO(new Bundle {
		val imem = new CacheBus(addrBits = VAddrBits, userBits = ICacheUserBundleWidth)
		val out = Decoupled(new CtrlFlowIO)
		val redirect = Flipped(new RedirectIO)
		val flushVec = Output(UInt(4.W))
		val bpFlush = Output(Bool())
		val ipf = Input(Bool())
		val bpuUpdate = Flipped(new BPUUpdate)
	})

	val pc = RegInit(resetVector.U(VAddrBits.W))
	val pcUpdate = io.redirect.valid || io.imem.req.fire
	val bpu = Module(new BPU_embedded)
	val snpc = pc + 4.U // sequential next PC
	val pnpc = bpu.io.out.target // predict next pc
	val npc = Mux(io.redirect.valid, io.redirect.target, Mux(bpu.io.out.valid, pnpc, snpc))

	bpu.io.in.pc.valid := io.imem.req.fire // only predict when ICache accepts a request
	bpu.io.in.pc.bits := npc // predict one cycle early
	bpu.io.flush := io.redirect.valid
	bpu.io.bpuUpdate <> io.bpuUpdate

	when (pcUpdate) { pc := npc }

	io.flushVec := Mux(io.redirect.valid, "b1111".U, 0.U)
	io.bpFlush := false.B

//	val icacheUserGen = Wire(new ICacheUserBundle)
//	icacheUserGen.pc := pc
//	icacheUserGen.pnpc := Mux(crosslineJump, nlp.io.out.target, npc)
//	icacheUserGen.brIdx := brIdx & pcInstValid
//	icacheUserGen.instValid := pcInstValid

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
//	io.out.bits.instr := io.imem.resp.bits.data

	if (Settings.get("TmpSet")) {
		io.out.bits.instr := io.imem.resp.bits.data >> io.out.bits.pc(2, 0) * 8.U
	}

	io.imem.resp.bits.user.map { case x =>
		io.out.bits.pc := x(2*VAddrBits-1, VAddrBits)
		io.out.bits.pnpc := x(VAddrBits-1, 0)
	}
	io.out.valid := io.imem.resp.valid && !io.flushVec(0)

	Debug(io.imem.req.fire, "[IFI] pc=%x redirect %x npc %x pc %x pnpc %x\n", io.imem.req.bits.addr, io.redirect.valid, npc, pc, bpu.io.out.target)
	Debug(io.out.fire, "[IFO] pc=%x inst=%x npc=%x ipf %x\n", io.out.bits.pc, io.out.bits.instr, io.out.bits.pnpc, io.ipf)
	Debug(io.redirect.valid, "[Redirect] target 0x%x rtype %b\n", io.redirect.target, io.redirect.rtype)

	// 需要实现，用于做性能计数器
//	BoringUtils.addSource(BoolStopWatch(io.imem.req.valid, io.imem.resp.fire), "perfCntCondMimemStall")
//	BoringUtils.addSource(WireInit(io.flushVec.orR), "perfCntCondMifuFlush")
}
