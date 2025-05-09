package module

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import top.Settings

trait HasRegFileParameter {
	val NRReg = 32
}

class RegFile extends HasRegFileParameter with HasMarCoreParameter {
	val rf = Mem(NRReg, UInt(XLEN.W))
	def read(addr: UInt): UInt = Mux(addr === 0.U, 0.U, rf(addr))
	def write(addr: UInt, data: UInt) = { rf(addr) := data(XLEN-1, 0)}

//	if (!Settings.get("IsChiselTest")) {
//		val gpr = Wire(Vec(NRReg, UInt(XLEN.W)))
//		for (i <- 0 until NRReg) gpr(i) := rf(i)
//		BoringUtils.addSource(gpr, "GPR")
//	}
}

class ScoreBoard extends HasRegFileParameter {
	val busy = RegInit(0.U(NRReg.W))
	def isBusy(idx: UInt): Bool = busy(idx)
	def mask(idx: UInt) = (1.U(NRReg.W) << idx)(NRReg-1, 0)
	def update(setMask: UInt, clearMask: UInt) = {
		// When clearMask(i) and setMask(i) are both set, setMask(i) wins.
		// This can correctly record the busy bit when reg(i) is written
		// and issued at the same cycle.
		// Note that rf(0) is always free.
		busy := Cat(((busy & ~clearMask) | setMask)(NRReg-1, 1), 0.U(1.W))
	}
}
