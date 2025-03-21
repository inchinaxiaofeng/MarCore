package bus.axi4

import chisel3._
import chisel3.util._

import defs._
import utils._

object AXI4Parameters extends HasMarCoreParameter {
	// These are all fixed by the AXI4 standard:
	/* 1~256 for burst type: INCR; 
	 * 1~16 for other burst type; 
	 * Only 2, 4, 8 or 16 is valid for burst type: WARP;
	 */
	val lenBits		= 8 // burst len
	val sizeBits	= 3 // 1b ~ 128b
	val burstBits	= 2
	val cacheBits	= 4
	val protBits	= 3
	val qosBits		= 4
	val respBits	= 2

	// These are not fixed:
	val idBits		= 4
	val addrBits	= PAddrBits
	val dataBits	= DataBits
	val userBits	= 1

	def CACHE_RALLOCATE		= 8.U(cacheBits.W)
	def CACHE_WALLOCATE		= 4.U(cacheBits.W)
	def CACHE_MODIFIABLE	= 2.U(cacheBits.W)
	def CACHE_BUFFERABLE	= 1.U(cacheBits.W)

	def PROT_PRIVILEDGED	= 1.U(protBits.W)
	def PROT_INSECURE		= 2.U(protBits.W)
	def PROT_BUFFERABLE		= 4.U(protBits.W)

	def BURST_FIXED	= 0.U(burstBits.W)
	def BURST_INCR	= 1.U(burstBits.W)
	def BURST_WRAP	= 2.U(burstBits.W)

	def RESP_OKAY	= 0.U(respBits.W)
	def RESP_EXOKEY	= 1.U(respBits.W)
	def RESP_SLVERR	= 2.U(respBits.W)
	def RESP_DECERR	= 3.U(respBits.W)
}

trait AXI4HasUser {
	val user = Output(UInt(AXI4Parameters.userBits.W))
}

trait AXI4HasData {
	def dataBits = AXI4Parameters.dataBits
	val data = Output(UInt(dataBits.W))
}

trait AXI4HasId {
	def idBits = AXI4Parameters.idBits
	val id = Output(UInt(idBits.W))
}

trait AXI4HasResp {
	val resp = Output(UInt(AXI4Parameters.respBits.W))
}

trait AXI4HasLast {
	val last = Output(Bool())
}

// AXI4-lite

class AXI4LiteBundleA extends Bundle {
	val addr = Output(UInt(AXI4Parameters.addrBits.W))
	val prot = Output(UInt(AXI4Parameters.protBits.W))

	def apply(addr: UInt = 0.U, prot: UInt = AXI4Parameters.PROT_PRIVILEDGED) = {
		this.addr := addr
		this.prot := prot
	}
}

class AXI4LiteBundleW(override val dataBits: Int = AXI4Parameters.dataBits) extends Bundle with AXI4HasData {
	val strb = Output(UInt((dataBits/8).W))

	def apply(data: UInt = 0.U, strb: UInt = 0.U) = {
		this.data := data
		this.strb := strb
	}
}

class AXI4LiteBundleB extends Bundle with AXI4HasResp{
	 def apply(resp: UInt = AXI4Parameters.RESP_OKAY) = {
		this.resp := resp
	}
}

class AXI4LiteBundleR(override val dataBits: Int = AXI4Parameters.dataBits) extends Bundle with AXI4HasData with AXI4HasResp {
	def apply(resp: UInt = AXI4Parameters.RESP_OKAY, data: UInt = 0.U) = {
		this.resp := resp
		this.data := data
	}
}

class AXI4Lite extends Bundle {
	val aw	= Decoupled(new AXI4LiteBundleA)
	val w	= Decoupled(new AXI4LiteBundleW)
	val b	= Flipped(Decoupled(new AXI4LiteBundleB))
	val ar	= Decoupled(new AXI4LiteBundleA)
	val r	= Flipped(Decoupled(new AXI4LiteBundleR))
}

// AXI4-full

class AXI4BundleA(override val idBits: Int = AXI4Parameters.idBits) extends AXI4LiteBundleA with AXI4HasId with AXI4HasUser {
	val len		= Output(UInt(AXI4Parameters.lenBits.W)) // number of beats - 1
	val size	= Output(UInt(AXI4Parameters.sizeBits.W)) // bytes in beat = 2^size
	val burst	= Output(UInt(AXI4Parameters.burstBits.W))
	val lock	= Output(Bool())
	val cache	= Output(UInt(AXI4Parameters.cacheBits.W))
	val qos		= Output(UInt(AXI4Parameters.qosBits.W)) // 0=no QoS, bigger = higher priority
	// val region = UInt(width = 4) // optional

	override def toPrintable: Printable = p"addr = 0x${Hexadecimal(addr)}, id = ${id}, len = ${len}, size = ${size}"
	def apply(addr: UInt, prot: UInt, id: UInt, user: UInt, len: UInt, size: UInt, burst: UInt, lock: UInt, cache: UInt, qos: UInt) = {
		this.addr := addr
		this.prot := prot
		this.id := id
		this.user := user
		this.len := len
		this.size := size
		this.burst := burst
		this.lock := lock
		this.cache := cache
		this.qos := qos
	}
}

// id ... removed in AXI4
class AXI4BundleW(override val dataBits: Int) extends AXI4LiteBundleW(dataBits) with AXI4HasId with AXI4HasLast {
	override def toPrintable: Printable = p"data = ${Hexadecimal(data)}, wmask = 0x${strb}, last = ${last}, id = ${id}"
	def apply(id: UInt, data: UInt, strb: UInt, last: UInt) = {
		this.id := id
		this.data := data
		this.strb := strb
		this.last := last
	}
}

class AXI4BundleB(override val idBits: Int) extends AXI4LiteBundleB with AXI4HasId with AXI4HasUser {
	override def toPrintable: Printable = p"resp = ${resp}, id = ${id}"
	def apply(resp: UInt, id: UInt, user: UInt) = {
		super.apply(resp = resp)
//		this.resp := resp
		this.id := id
		this.user := user
	}
}

class AXI4BundleR(override val dataBits: Int, override val idBits: Int) extends AXI4LiteBundleR(dataBits) with AXI4HasLast with AXI4HasId with AXI4HasUser {
	override def toPrintable: Printable = p"resp = ${resp}, id = ${id}, data = ${Hexadecimal(data)}, last = ${last}"
	def apply(resp: UInt, data: UInt, last: UInt, id: UInt, user: UInt) = {
		this.resp := resp
		this.data := data
		this.last := last
		this.id := id
		this.user := user
	}
}

class AXI4(val dataBits: Int = AXI4Parameters.dataBits, val idBits: Int = AXI4Parameters.idBits) extends AXI4Lite {
	override val aw	= Decoupled(new AXI4BundleA(idBits))
	override val w	= Decoupled(new AXI4BundleW(dataBits))
	override val b	= Flipped(Decoupled(new AXI4BundleB(idBits)))
	override val ar	= Decoupled(new AXI4BundleA(idBits))
	override val r	= Flipped(Decoupled(new AXI4BundleR(dataBits, idBits)))

	def dump(name: String) = {
		when (aw.fire)	{ printf(p"${GTimer()},[${name}.aw] ${aw.bits}\n") }
		when (w.fire)	{ printf(p"${GTimer()},[${name}.w] ${w.bits}\n") }
		when (b.fire)	{ printf(p"${GTimer()},[${name}.b] ${b.bits}\n") }
		when (ar.fire)	{ printf(p"${GTimer()},[${name}.ar] ${ar.bits}\n") }
		when (r.fire)	{ printf(p"${GTimer()},[${name}.r] ${r.bits}\n") }
	}
}
