package module.fu.mips

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import top.Settings

object MDUCtrl {
/* [4]: None.
   [3]: Word bit.
   [2]: Div bit.
   [1]:
   [0]: Div sign bit, 0 means signed, 1 means unsigned
*/
	//				3210
	def mul		= "b0000".U

	def isDiv(op: UInt) = op(2)
	def isDivSign(op: UInt) = isDiv(op) && !op(0)
	def isW(op: UInt) = op(3)
}

class MulDivIO(val len: Int) extends Bundle {
	val in = Flipped(DecoupledIO(Vec(2, Output(UInt(len.W)))))
	val sign = Input(Bool())
	val out = DecoupledIO(Output(UInt((len * 2).W)))
}

class Multiplier(len: Int) extends MarCoreModule {
	println("This Fu Unit is for [MIPS-C3 32 Bit]")
	require(XLEN == 32)
	implicit val moduleName: String = this.name
	val io = IO(new MulDivIO(len))
//	val latency = 1

	val mulRes = (io.in.bits(0).asSInt * io.in.bits(1).asSInt).asSInt
	io.out.bits := mulRes.asUInt
	io.out.valid := true.B
	io.in.ready := io.in.valid
}

class MDUIO extends FuCtrlIO { }

class MDU extends MarCoreModule {
	implicit val moduleName: String = this.name
	val io = IO(new MDUIO)

	val (valid, srcA, srcB, ctrl) = (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
	def access (valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
		this.valid := valid
		this.srcA := srcA
		this.srcB := srcB
		this.ctrl := ctrl
		io.out.bits
	}

	val isDiv = MDUCtrl.isDiv(ctrl)
	val isDivSign = MDUCtrl.isDivSign(ctrl)
	val isW = MDUCtrl.isW(ctrl)

	val mul = Module(new Multiplier(XLEN + 1))
	mul.io.sign := isDivSign
	mul.io.out.ready := io.out.ready

	val signext = SignExt(_: UInt, XLEN + 1)
	val zeroext = ZeroExt(_: UInt, XLEN + 1)
	val mulInputFuncTable = List(
		MDUCtrl.mul		-> (signext, signext)
	)
	mul.io.in.bits(0) := LookupTree(ctrl(1, 0), mulInputFuncTable.map(p => (p._1(1, 0), p._2._1(srcA))))
	mul.io.in.bits(1) := LookupTree(ctrl(1, 0), mulInputFuncTable.map(p => (p._1(1, 0), p._2._2(srcB))))

	mul.io.in.valid := io.in.valid && !isDiv

	val res = mul.io.out.bits(XLEN-1, 0)
	io.out.bits := Mux(isW, SignExt(res(31, 0), XLEN), res(XLEN-1, 0))

	val isDivReg = Mux(io.in.fire, isDiv, RegNext(isDiv))
	io.in.ready := mul.io.in.ready // Mux(isDiv, div.io.in.ready, mul.io.in.ready)
	io.out.valid := mul.io.out.valid // Mux(isDivReg, div.io.out.valid, mul.io.out.valid)
}
