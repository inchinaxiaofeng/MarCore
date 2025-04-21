package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import top.Settings

/** 通用化的 MDU 編碼結構。通過設計 MDU 指令，將ISA與架構實現分離。
  *
  * 考慮到主要的RISC架構中,都將 W 后缀設計爲 64-bit 下的特产，专为处理 legacy 32-bit 数据而设，32 位下無效或不存在.
  *
  * 因此, 我們在這裏保持一致, W 後綴只有在 64-bit 下才有意義, 32-bit 下不應該使用
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @param `[6]`
  *   Adder sub bit. 加法器控制位, 可以通過這一位判斷當前是加還是減. 當拉高時, 加法器將進行減法.
  *
  * @param `[5]`
  *   Word bit. XLEN == 64 時, 當這一位拉高時，指 Word 數據類型. XLEN == 32時, 這一位被拋棄.
  *
  * @param `[4]`
  *   Unsigned bit. 無符號標誌位. 當這一位拉高時, 對 可拓展原數據 進行0拓展, 否則將會使用符號拓展
  *
  * @param `[3,0]`
  *   Encoded type bit. 編碼後類型, 用於在無法通過其他位分別時, 區分不同指令. 當編碼全爲 0 時用於區分不需要進行匹配
  */

object MDUCtrl {
  /* [4]: None.
   [3]: Word bit.
   [2]: Div bit.
   [1]:
   [0]: Div sign bit, 0 means signed, 1 means unsigned
   */
  //				3210
  def mul = "b0000".U
  def mulh = "b0001".U
  def mulhsu = "b0010".U
  def mulhu = "b0011".U
  def div = "b0100".U
  def divu = "b0101".U
  def rem = "b0110".U
  def remu = "b0111".U

  def mulw = "b1000".U
  def divw = "b1100".U
  def divuw = "b1101".U
  def remw = "b1110".U
  def remuw = "b1111".U

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
  implicit val moduleName: String = this.name
  val io = IO(new MulDivIO(len))
//	val latency = 1

  val mulRes = (io.in.bits(0).asSInt * io.in.bits(1).asSInt).asSInt
  io.out.bits := mulRes.asUInt
  io.out.valid := true.B
  io.in.ready := io.in.valid
}

class Divider(len: Int) extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new MulDivIO(len))

//	io.out.bits := (io.in.bits(0).asSInt / io.in.bits(1).asSInt).asUInt

  val resQ = io.in.bits(0) / io.in.bits(1)
  val resR = io.in.bits(0) % io.in.bits(1)

  io.out.bits := Cat(resR, resQ)

  io.out.valid := true.B
  io.out.valid := true.B
  io.in.ready := io.in.valid
}

class MDUIO extends FuCtrlIO {}

class MDU extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new MDUIO)

  val (valid, srcA, srcB, ctrl) =
    (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
  def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
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
  val div = Module(new Divider(XLEN))
  List(mul.io, div.io).map { case x =>
    x.sign := isDivSign
    x.out.ready := io.out.ready
  }

  val signext = SignExt(_: UInt, XLEN + 1)
  val zeroext = ZeroExt(_: UInt, XLEN + 1)
  val mulInputFuncTable = List(
    MDUCtrl.mul -> (zeroext, zeroext),
    MDUCtrl.mulh -> (signext, signext),
    MDUCtrl.mulhsu -> (signext, zeroext),
    MDUCtrl.mulhu -> (zeroext, zeroext)
  )
  mul.io.in.bits(0) := LookupTree(
    ctrl(1, 0),
    mulInputFuncTable.map(p => (p._1(1, 0), p._2._1(srcA)))
  )
  mul.io.in.bits(1) := LookupTree(
    ctrl(1, 0),
    mulInputFuncTable.map(p => (p._1(1, 0), p._2._2(srcB)))
  )

  val divInputFunc = (x: UInt) =>
    Mux(
      isW,
      Mux(isDivSign, SignExt(x(31, 0), XLEN), ZeroExt(x(31, 0), XLEN)),
      x
    )
  div.io.in.bits(0) := divInputFunc(srcA)
  div.io.in.bits(1) := divInputFunc(srcB)

  mul.io.in.valid := io.in.valid && !isDiv
  div.io.in.valid := io.in.valid && isDiv

  val mulRes = Mux(
    ctrl(1, 0) === MDUCtrl.mul(1, 0),
    mul.io.out.bits(XLEN - 1, 0),
    mul.io.out.bits(2 * XLEN - 1, XLEN)
  )
  val divRes = Mux(
    ctrl(1),
    div.io.out.bits(2 * XLEN - 1, XLEN),
    div.io.out.bits(XLEN - 1, 0)
  )
  val res = Mux(isDiv, divRes, mulRes)
  io.out.bits := Mux(isW, SignExt(res(31, 0), XLEN), res)

  val isDivReg = Mux(io.in.fire, isDiv, RegNext(isDiv))
  io.in.ready := Mux(isDiv, div.io.in.ready, mul.io.in.ready)
  io.out.valid := Mux(isDivReg, div.io.out.valid, mul.io.out.valid)

//	BoringUtils.addSource(WireInit(mul.io.out.fire), "perfCntCondMmulInstr")
}
