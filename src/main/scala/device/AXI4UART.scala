package device

import chisel3._
import chisel3.util._

import defs._
import top._
import utils._
import bus.axi4._
import device._

class AXI4UART extends AXI4SlaveModule(new AXI4Lite, _extra = new UARTIO) {
	val rxfifo = RegInit(0.U(32.W))
	val txfifo = Reg(UInt(32.W))
	val stat = RegInit(1.U(32.W))
	val ctrl = RegInit(0.U(32.W))

	io.extra.get.out.valid := (waddr(3,0) === 4.U && in.w.fire)
	io.extra.get.out.ch := in.w.bits.data(7,0)
	io.extra.get.in.valid := (raddr(3,0) === 0.U && in.r.fire)

	val mapping = Map(
		RegMap(0x0, io.extra.get.in.ch, RegMap.Unwritable),
		RegMap(0x4, txfifo),
		RegMap(0x8, stat),
		RegMap(0xc, ctrl)
	)
	
	RegMap.generate(mapping, raddr(3,0), in.r.bits.data,
		waddr(3,0), in.w.fire, in.w.bits.data(31,0), MaskExpend(in.w.bits.strb >> waddr(2,0))(31,0)
  )
}
