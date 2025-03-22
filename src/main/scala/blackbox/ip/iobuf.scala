package blackbox.ip

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.axi4._

class IOBUF extends BlackBox {
	val io = IO(new Bundle {
		val IO = ???
		val I = ???
		val T = ???
		val O = ???
	})
}
