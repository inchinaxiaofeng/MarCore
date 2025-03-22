package module.fu.mips

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import module._

object LSUCtrl {
	//            "b6543210"
	def lb		= "b0000000".U
	def lw		= "b0000010".U
	def sb		= "b0001000".U
	def sw		= "b0001010".U

	def isAdd(ctrl: UInt) = ctrl(6)
	def isAtom(ctrl: UInt): Bool = ctrl(5)
	def isStore(ctrl: UInt): Bool = ctrl(3)
	def isLoad(ctrl: UInt): Bool = !isStore(ctrl) & !isAtom(ctrl)

	def needMemRead(ctrl: UInt): Bool = isLoad(ctrl)
	def needMemWrite(ctrl: UInt): Bool = isStore(ctrl)
}

trait HasLSUConst {
	val IndependentAddrCalcState = false
	val moqSize = 8
	val storeQueueSize = 8	
}
