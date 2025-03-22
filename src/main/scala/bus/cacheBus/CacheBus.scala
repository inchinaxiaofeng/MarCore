package bus.cacheBus

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.axi4._

/* len = 0, Endless burst
 * resp
 */

object CacheBusParameters extends HasMarCoreParameter {
	val addrBits = PAddrBits
	val dataBits = DataBits
	val respBits = 1
	val lenBits = 8
	val sizeBits = 3

	def RESP_DONTCARE	= 0.U(respBits.W)
}

sealed abstract class CacheBusBundle extends Bundle with HasMarCoreParameter
/* 对于CacheBus要求，len、last、size在任何一个传输中都需要正确的设置。
 * CacheBus是基于突发传输的总线协议。
 * Trans Event:
	1, 通信过程中，len信号、size信号需要保持不变，直到数据传输完成。
	始终保持 last 为 false.B；当发送最后一个数据时，拉高 last 表明传输结束。
 */

class CacheBusReqBundle(val userBits: Int = 0, val idBits: Int = 0, val addrBits: Int = CacheBusParameters.addrBits, val dataBits: Int = CacheBusParameters.dataBits, val lenBits: Int = CacheBusParameters.lenBits, val sizeBits: Int = CacheBusParameters.sizeBits) extends CacheBusBundle {
	val user = if (userBits > 0) Some(Output(UInt(userBits.W))) else None
	val id = if (idBits > 0) Some(Output(UInt(idBits.W))) else None
	val addr = Output(UInt(addrBits.W))		// Read Write
	val len = Output(UInt(lenBits.W))		// Read Write
	val size = Output(UInt(sizeBits.W))
	val data = Output(UInt(dataBits.W))		// Write Only
	val strb = Output(UInt((dataBits/8).W))	// Write Only
	val last = Output(Bool())				// Write Only
	val write = Output(Bool())

	override def toPrintable: Printable = {
		p"addr = 0x${Hexadecimal(addr)}, len = ${len}, write = ${write}, strb = 0x${Hexadecimal(strb)}, data = 0x${Hexadecimal(data)}"
	}

	def apply(user: UInt = 0.U, id: UInt = 0.U, addr: UInt, len: UInt, size: UInt, data: UInt = 0.U, strb: UInt = 0.U, last: Bool, write: Bool) = {
		this.user.map(_ := user)
		this.id.map(_ := id)
		this.addr := addr
		this.len := len
		this.size := size
		this.data := data
		this.strb := strb
		this.last := last
		this.write := write
	}
}

class CacheBusRespBundle(val userBits: Int = 0, val idBits: Int = 0, val addrBits: Int = CacheBusParameters.addrBits, val dataBits: Int = CacheBusParameters.dataBits, val respBits: Int = CacheBusParameters.respBits) extends CacheBusBundle {
	val user = if (userBits > 0) Some(Output(UInt(userBits.W))) else None
	val id = if (idBits > 0) Some(Output(UInt(idBits.W))) else None
	val data = Output(UInt(dataBits.W))	// Read Only
	val last = Output(Bool())			// Read Only
	val write = Output(Bool())

	override def toPrintable: Printable = {
		p"data = 0x${data}, write = ${write}"
	}
}

class CacheBus(val userBits: Int = 0, val idBits: Int = 0, val addrBits: Int = CacheBusParameters.addrBits, val dataBits: Int = CacheBusParameters.dataBits, val lenBits: Int = CacheBusParameters.lenBits, val respBits: Int = CacheBusParameters.respBits) extends CacheBusBundle {
	val req = Decoupled(new CacheBusReqBundle(userBits = userBits, idBits = idBits, addrBits = addrBits, dataBits = dataBits, lenBits = lenBits))
	val resp = Flipped(Decoupled(new CacheBusRespBundle(userBits = userBits, idBits = idBits, addrBits = addrBits, dataBits = dataBits, respBits = respBits)))

	def toAXI4Lite = CacheBus2AXI4Converter(this, new AXI4Lite, false)
	def toAXI4(isFromCache: Boolean = false) = CacheBus2AXI4Converter(this, new AXI4, isFromCache)

	def dump(name: String) = {
		when (req.fire) { printf(p"${GTimer()}, [${name}] ${req.bits}\n") }
		when (resp.fire) { printf(p"${GTimer()}, [${name}] ${resp.bits}\n") }
	}
}
