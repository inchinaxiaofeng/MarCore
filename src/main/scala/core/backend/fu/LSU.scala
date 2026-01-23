package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._

/** 通用化的 LSU 控制
  *
  * @param `[6]`
  *   Store bit. 拉高時代表Store操作.
  *   - 原子操作下, 拉高時代表Store-Conditional, 反之Load-Reserved
  *
  * @param `[5]`
  *   Atom Bit. 原子操作位. 拉高時代表Atom操作. 當拉高時, `[6]` 表示
  *
  * @param `[4]`
  *   Unsigned bit. 無符號標誌位. 當這一位拉高時, 對 可拓展原數據 進行0拓展, 否則將會使用符號拓展
  *
  * @param `[3]`
  *   Pre Load. 預取標誌位. 拉高這一位時, 表示需要預取.
  *
  * @param `[1:0]`
  *   Byte bits. 字節映射位. 代表操作的字節數.
  */
object LSUCtrl {
  def lb = "b0000_0_00".U
  def lh = "b0000_0_01".U
  def lw = "b0000_0_10".U
  def ld = "b0000_0_11".U
  def lbu = "b0010_0_00".U
  def lhu = "b0010_0_01".U
  def lwu = "b0010_0_10".U

  def sb = "b1000_0_00".U
  def sh = "b1000_0_01".U
  def sw = "b1000_0_10".U
  def sd = "b1000_0_11".U

  def lr = "b0100_000".U
  def sc = "b1100_000".U

  def preld = "b0001_0_00".U

  def isStore(ctrl: UInt) = ctrl(6)
  def isLoad(ctrl: UInt) = !ctrl(6)
  def isAtom(ctrl: UInt) = ctrl(5)
  def isUnsigned(ctrl: UInt) = ctrl(4)
  def isPreld(ctrl: UInt) = ctrl(3)
  def getEncoded(ctrl: UInt) = ctrl(1, 0)

  def needMemRead(ctrl: UInt): Bool = isLoad(
    ctrl
  )
  def needMemWrite(ctrl: UInt): Bool = isStore(
    ctrl
  )
}

trait HasLSUConst {
  val IndependentAddrCalcState = false
  val moqSize = 8
  val storeQueueSize = 8
}
