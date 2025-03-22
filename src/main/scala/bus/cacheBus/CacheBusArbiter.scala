package bus.cacheBus

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import bus.cacheBus._

class CacheBusArbiter extends MarCoreModule {
	implicit val moduleName: String = this.name
	val InstFetch	= IO(Flipped(new CacheBus))
	val LoadStore	= IO(Flipped(new CacheBus))
	val MMIO		= IO(Flipped(new CacheBus))
	val OUT			= IO(new CacheBus)

	val arb = Module(new Arbiter(chiselTypeOf(new CacheBus), 3))

	arb.io.in(0) <> MMIO
	arb.io.in(1) <> LoadStore
	arb.io.in(2) <> InstFetch

	OUT <> arb.io.out

}
