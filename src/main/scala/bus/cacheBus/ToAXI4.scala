package bus.cacheBus

import chisel3._
import chisel3.util._

import defs._
import utils._
import module.cache._
import bus.cacheBus._
import bus.axi4._

class CacheBus2AXI4Converter[OT <: AXI4Lite](outType: OT, isFromCache: Boolean) extends Module {
	implicit val moduleName: String = this.name
	val io = IO(new Bundle {
		val in = Flipped(new CacheBus)
		val out = Flipped(Flipped(outType))
	})

	val toAXI4Lite = !(io.in.req.valid && io.in.req.bits.len =/= 1.U) && (outType.getClass == classOf[AXI4Lite]).B
	val toAXI4 = (outType.getClass == classOf[AXI4]).B
	assert(toAXI4Lite || toAXI4)

	val (mem, axi) = (io.in, io.out)
	val (ar, aw, w, r, b) = (axi.ar.bits, axi.aw.bits, axi.w.bits, axi.r.bits, axi.b.bits)

	ar.addr	:= mem.req.bits.addr
	ar.prot	:= AXI4Parameters.PROT_PRIVILEDGED
	w.data	:= mem.req.bits.data
	w.strb	:= mem.req.bits.strb

	// NOTE: 根据CacheBus中的数据调整
	def LineBeats = 4
	val wlast = WireInit(true.B)
	val rlast = WireInit(true.B)
	if (outType.getClass == classOf[AXI4]) {
		val axi4 = io.out.asInstanceOf[AXI4]
		axi4.ar.bits.id		:= 0.U
		axi4.ar.bits.len	:= mem.req.bits.len
		axi4.ar.bits.size	:= mem.req.bits.size
		axi4.ar.bits.burst	:= (if (isFromCache) AXI4Parameters.BURST_WRAP
								else AXI4Parameters.BURST_FIXED)
		axi4.ar.bits.lock	:= false.B
		axi4.ar.bits.cache	:= 0.U
		axi4.ar.bits.qos	:= 0.U
		axi4.ar.bits.user	:= 0.U
		axi4.w.bits.last	:= mem.req.bits.write && mem.req.bits.last
		axi4.w.bits.id		:= 0.U
		wlast := axi4.w.bits.last
		rlast := axi4.r.bits.last
	}

	val wSend	= Wire(Bool())
	val awAck	= BoolStopWatch(axi.aw.fire, wSend)
	val wAck	= BoolStopWatch(axi.w.fire && wlast, wSend)
	val wen 	= RegEnable(mem.req.bits.write, mem.req.fire)
	wSend := (axi.aw.fire && axi.w.fire && wlast) || (awAck && wAck)

	aw := ar
	mem.resp.bits.data	:= r.data
	mem.resp.bits.last	:= rlast
	mem.resp.bits.write	:= wen

	axi.ar.valid := !mem.req.bits.write && mem.req.valid
	axi.aw.valid := mem.req.bits.write && !awAck && mem.req.valid
	axi.w.valid  := mem.req.bits.write && !wAck && mem.req.valid
	mem.req.ready := Mux(mem.req.bits.write, !wAck && axi.w.ready, axi.ar.ready)

	axi.r.ready := mem.resp.ready
	axi.b.ready := mem.resp.ready
	mem.resp.valid := Mux(wen, axi.b.valid, axi.r.valid)
}

object CacheBus2AXI4Converter {
	def apply[OT <: AXI4Lite](in: CacheBus, outType: OT, isFromCache: Boolean = false): OT = {
		val bridge = Module(new CacheBus2AXI4Converter(outType, isFromCache))
		bridge.io.in <> in
		bridge.io.out
	}
}
