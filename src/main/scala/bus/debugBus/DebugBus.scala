package bus.debugBus

import chisel3._
import chisel3.util._

import defs._
import utils._

object DebugBusParameters extends HasMarCoreParameter {
	/* valid ready 握手信号仅仅用于信号是否通信，在正常情况下，无论能否找到，都应该有valid信号拉高  */
	val addrBits = VAddrBits
	val dataBits = DataBits
	val userBits = DataBits
	val lenBits = 3

	val idBits	= 4
	def L1ICache	= "b0000".U
	def L1DCache	= "b0001".U
	def L2Cache		= "b0010".U

	val cmdBits = 4
	// Cache
	def SCAN_META		= "b0000".U	// Scan Meta Array
	def SCAN_DATA		= "b0001".U	// Scan Data Array
	def SHOW_CTRL		= "b0010".U // Show Ctrl Signal
	def WRITE			= "b0011".U // TODO
	def STOP			= "b0100".U // STOP Current thing.

	/* We have four prio level: (1)error, (2)prio, (3)normal bad and (4)ignore. 
	 * For Level Error, Prio, Normal, every cycle only should has one resp at same level.
	 * If has multi Normal resp, set Error Level resp: MULTI_NORMAL.
	 * If has multi Prio resp, set Error Level resp: MULTI_PRIO.
	 * If has multi Error resp, set Error Level resp: MULTI_ERROR.
	 * If every slave set resp: SLAVE_NOT_ME, set Ingore Level resp: SLAVE_NOT_ME.
	 * If Any Higher prio level resp exist, set current slave as the highest level resp.
	 * If have multi error, set Error Level resp: MULTI_ERROR.
	 */
	val respBits = 5
	/* General resp. Should be ignore if other resp exist. Level ignore. */
	def SLAVE_NOT_ME	= "b00_000".U
	/* Everything is good, only in this resp state, data is valid. Level normal */
	def SLAVE_OKAY		= "b01_000".U
	/* Find me but. Level normal */
	def SLAVE_DONTCARE	= "b01_001".U // Dont Care, Placeholder for TODO module
	def SLAVE_ADDR_OOB	= "b01_010".U // Addr Out Of Bound
	def SLAVE_UNSPT_CMD	= "b01_011".U // Unsupport Cmd at current time, but will support in future. TODO Placeholder.
	/* Wait me, Level prio. */
	def WAIT_FOR_DATA	= "b10_000".U // Wait for Preparing some data.
	def WAIT_NEED_VALID	= "b10_001".U // Wait for all slave is valid.
	/* Error, error level */
	def MULTI_NORMAL	= "b11_000".U // If more than one slave at Normal Level resp, set MULTI_NORMAL resp in Resp.
	def MULTI_PRIO		= "b11_001".U // If more than one slave at Prio Level resp, set MULTI_PRIO resp in Resp.
	def MULTI_ERROR		= "b11_010".U // If more than one slave at Error Level resp, set MULTI_ERROR resp in Resp.
	def UNDEFINED		= "b11_111".U

	def isError(resp: UInt)		= resp(4) && resp(3)		// 11
	def isPrio(resp: UInt)		= resp(4) && !resp(3)		// 10
	def isNormal(resp: UInt)	= !resp(4) && resp(3)	// 01
	def isIgnore(resp: UInt)	= !resp(4) && !resp(3)	// 00
}

sealed abstract class DebugBusBundle extends Bundle with HasMarCoreParameter

class DebugBusReqBundle(
	val userBits: Int	= DebugBusParameters.userBits	,
	val idBits: Int		= DebugBusParameters.idBits		,
	val addrBits: Int	= DebugBusParameters.addrBits	,
	val dataBits: Int	= DebugBusParameters.dataBits	,
	val lenBits: Int	= DebugBusParameters.lenBits 	,
	val cmdBits: Int	= DebugBusParameters.cmdBits
) extends DebugBusBundle {
	val user	= Output(UInt(userBits.W))
	val id		= Output(UInt(idBits.W))
	val addr	= Output(UInt(addrBits.W))
	val len		= Output(UInt(lenBits.W))
	val data	= Output(UInt(dataBits.W))
	val cmd		= Output(UInt(cmdBits.W))
	val strb	= Output(UInt((dataBits/8).W))
}

class DebugBusRespBundle(
	val userBits: Int	= DebugBusParameters.userBits	,
	val idBits: Int		= DebugBusParameters.idBits		,
	val dataBits: Int	= DebugBusParameters.dataBits	,
	val respBits: Int	= DebugBusParameters.respBits	,
	val lenBits: Int	= DebugBusParameters.lenBits
) extends DebugBusBundle {
	val user	= Output(UInt(userBits.W))
	val id		= Output(UInt(idBits.W))
	val resp	= Output(UInt(respBits.W))
	val datas	= Vec(8, Output(UInt(dataBits.W)))
	val len		= Output(UInt(lenBits.W))
}

class DebugBus(
	val userBits: Int	= DebugBusParameters.userBits	,
	val idBits: Int		= DebugBusParameters.idBits		,
	val addrBits: Int	= DebugBusParameters.addrBits	,
	val dataBits: Int	= DebugBusParameters.dataBits	,
	val lenBits: Int	= DebugBusParameters.lenBits	,
	val respBits: Int	= DebugBusParameters.respBits
) extends DebugBusBundle {
	val req = Decoupled(new DebugBusReqBundle(
		userBits	= userBits	,
		idBits		= idBits	,
		addrBits	= addrBits	,
		dataBits	= dataBits	,
		lenBits		= lenBits
	))
	val resp = Flipped(Decoupled(new DebugBusRespBundle(
		userBits	= userBits	,
		idBits		= idBits	,
		dataBits	= dataBits	,
		respBits	= respBits	,
		lenBits		= lenBits
	)))
}
