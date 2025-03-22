package bus.debugBus

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.debugBus.{DebugBus}

class DebugBusCrossbar1toN(n: Int, userBits: Int = 0) extends Module {
	implicit val moduleName: String = this.name
	val io = IO(new Bundle {
		val in = Flipped(new DebugBus(userBits))
		val outs = Vec(n, new DebugBus(userBits))
	})

	// req
	for (out <- io.outs) {
		out.req.valid := io.in.req.valid
		out.req.bits := io.in.req.bits
	}
	io.in.req.ready := io.outs.map(_.req.ready).reduce(_ || _)

	// resp
	val validCnt	= io.outs.count(_.resp.valid)
	val errorCnt	= io.outs.count(out => DebugBusParameters.isError(out.resp.bits.resp).asBool)
	val prioCnt		= io.outs.count(out => DebugBusParameters.isPrio(out.resp.bits.resp).asBool)
	val normalCnt	= io.outs.count(out => DebugBusParameters.isNormal(out.resp.bits.resp).asBool)
	val ignoreCnt	= io.outs.count(out => DebugBusParameters.isIgnore(out.resp.bits.resp).asBool)


//	Debug("bits %d\n", cntBits.U)
//	Debug("outs(0).resp (%d,%d) outs(1).resp (%d,%d)\n", io.outs(0).resp.valid, io.outs(0).resp.ready, io.outs(1).resp.valid, io.outs(1).resp.ready)
//	Debug("n %d validCnt %d errorCnt %d prioCnt %d normalCnt %d ignoreCnt %d\n", n.U, validCnt, errorCnt, prioCnt, normalCnt, ignoreCnt)
//	Debug("resp: %b,%b\n", io.outs(0).resp.bits.resp, io.outs(1).resp.bits.resp)
//	Debug("outs.error %d,%d outs.prio %d,%d outs.normal %d,%d outs.ignore %d,%d\n", DebugBusParameters.isError(io.outs(0).resp.bits.resp), DebugBusParameters.isError(io.outs(1).resp.bits.resp), DebugBusParameters.isPrio(io.outs(0).resp.bits.resp), DebugBusParameters.isPrio(io.outs(1).resp.bits.resp), DebugBusParameters.isNormal(io.outs(0).resp.bits.resp), DebugBusParameters.isNormal(io.outs(1).resp.bits.resp), DebugBusParameters.isIgnore(io.outs(0).resp.bits.resp), DebugBusParameters.isIgnore(io.outs(1).resp.bits.resp))

	val errorBits	= Mux1H(io.outs.map(out => DebugBusParameters.isError(out.resp.bits.resp)) , io.outs.map(_.resp.bits))
	val prioBits	= Mux1H(io.outs.map(out => DebugBusParameters.isPrio(out.resp.bits.resp))  , io.outs.map(_.resp.bits))
	val normalBits	= Mux1H(io.outs.map(out => DebugBusParameters.isNormal(out.resp.bits.resp)), io.outs.map(_.resp.bits))
	val ignoreBits	= Mux1H(io.outs.map(out => DebugBusParameters.isIgnore(out.resp.bits.resp)), io.outs.map(_.resp.bits))

	// Every UNDEFINED Error should not be reach.
	val errorResp	= Mux(errorCnt === 1.U, errorBits.resp, Mux(errorCnt =/= 0.U, DebugBusParameters.MULTI_ERROR, DebugBusParameters.UNDEFINED))
	val prioResp	= Mux(validCnt =/= n.U,
		DebugBusParameters.WAIT_NEED_VALID,
		Mux(prioCnt === 1.U, prioBits.resp, Mux(prioCnt =/= 0.U, DebugBusParameters.MULTI_PRIO, DebugBusParameters.UNDEFINED))
	)
	val normalResp	= Mux(normalCnt === 1.U, normalBits.resp, Mux(normalCnt =/= 0.U, DebugBusParameters.MULTI_NORMAL, DebugBusParameters.UNDEFINED))
	val ignoreResp	= Mux(ignoreCnt === n.U, DebugBusParameters.SLAVE_NOT_ME, DebugBusParameters.UNDEFINED)


	io.in.resp.valid := validCnt =/= 0.U
//	io.in.resp.bits := Mux1H(io.outs.map(DebugBusParameters.isNormal().resp.valid), io.outs.map(_.resp.bits))

	// Should last
	io.in.resp.bits := Mux(errorCnt =/= 0.U, errorBits, Mux(prioCnt =/= 0.U, prioBits, Mux(normalCnt =/= 0.U, normalBits, Mux(ignoreCnt =/= 0.U, ignoreBits, ignoreBits))))
	io.in.resp.bits.resp := Mux(errorCnt =/= 0.U, errorResp, Mux(prioCnt =/= 0.U, prioResp, Mux(normalCnt =/= 0.U, normalResp, Mux(ignoreCnt =/= 0.U, ignoreResp, DebugBusParameters.UNDEFINED))))
	for (out <- io.outs) {
		out.resp.ready := io.in.resp.ready
	}
}
