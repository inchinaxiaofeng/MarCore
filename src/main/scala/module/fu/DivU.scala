package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import utils.fu._
import top.Settings

/** 通用化的 MULU 編碼結構。通過設計 MULU 指令，將ISA與架構實現分離。
  *
  * 考慮到主要的RISC架構中,都將 W 后缀設計爲 64-bit 下的特产，专为处理 legacy 32-bit 数据而设，32 位下無效或不存在.
  *
  * 因此, 我們在這裏保持一致, W 後綴只有在 64-bit 下才有意義, 32-bit 下不應該使用
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @param `[6]`
  *   Rem bit. 餘位標誌. 拉高時獲取除法器的餘位數據
  *
  * @param `[5]`
  *   Word bit. XLEN == 64 時, 當這一位拉高時，指 Word 數據類型. XLEN == 32時, 這一位被拋棄.
  *
  * @param `[4]`
  *   Unsigned bit. 無符號標誌位. 拉高時, 操作數進行一位的0拓展, 否則將會使用符號拓展
  *
  * @param `[3,0]`
  *   保留
  */

object DivUCtrl {
  def div = "b000_0000".U
  def divu = "b001_0000".U
  def divw = "b010_0000".U
  def divuw = "b011_0000".U

  def rem = "b100_0000".U
  def remu = "b101_0000".U
  def remw = "b110_0000".U
  def remuw = "b111_0000".U

  /** 取餘使能標誌位
    *
    * @param ctrl
    * @return
    */
  def isRem(ctrl: UInt) = ctrl(6)

  /** Word Bit. 字操作使能位
    *
    * @param ctrl
    * @return
    */
  def isW(ctrl: UInt) = ctrl(5)

  /** 0 拓展使能位
    *
    * @param ctrl
    * @return
    */
  def isZero(ctrl: UInt) = ctrl(4)
}

class DivUIO extends FuCtrlIO {}

class DivU extends MarCoreModule {
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

  val isRem = DivUCtrl.isRem(ctrl)
  val isW = DivUCtrl.isW(ctrl)
  val isZero = DivUCtrl.isZero(ctrl)

  val divInputFunc = (x: UInt) =>
    Mux(
      isW,
      Mux(isZero, ZeroExt(x(31, 0), XLEN), SignExt(x(31, 0), XLEN)),
      x
    )

  val resQ = divInputFunc(srcA) / divInputFunc(srcB)
  val resR = divInputFunc(srcA) % divInputFunc(srcB)
  val res = Mux(isRem, resR, resQ)

  io.out.bits := Mux(isW, SignExt(res(31, 0), XLEN), res)
  io.in.ready := true.B
  io.out.valid := io.in.valid

//	BoringUtils.addSource(WireInit(mul.io.out.fire), "perfCntCondMmulInstr")
}
