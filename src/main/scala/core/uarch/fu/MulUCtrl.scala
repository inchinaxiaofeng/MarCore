package core.uarch.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._

/** 通用化的 MULU 編碼結構。通過設計 MULU 指令，將ISA與架構實現分離。
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @param `[6]`
  *   High bit. 高位標誌. 拉高時獲取乘法器的高位數據(XLEN*2-1, XLEN)
  *
  * @param `[5]`
  *   Word bit. XLEN == 64 時, 當這一位拉高時，指 Word 數據類型. XLEN == 32時, 這一位被拋棄.
  *
  * @param `[4:3]`
  *   Unsigned bit. 無符號標誌位.
  *   - 當其中一位拉高時, 對對應操作數進行一位的0拓展, 否則將會使用符號拓展
  *   - 其中`[4]`代表srcA, `[3]`代表srcB.
  *
  * @param `[2,0]`
  *   保留
  */
object MulUCtrl {
  // 這裏被設計爲無符號拓展在於可以節約邏輯
  def mul = "b0011_000".U
  def mulw = "b0111_000".U

  def mulh = "b1000_000".U
  def mulhw = "b1100_000".U

  def mulhu = "b1011_000".U
  def mulhwu = "b1111_000".U

  def mulhsu = "b1001_000".U

  /** 高位輸出使能位
    *
    * @param ctrl
    * @return
    */
  def isHigh(ctrl: UInt) = ctrl(6)

  /** Word Bit 拓展使能位
    *
    * @param ctrl
    * @return
    */
  def isW(ctrl: UInt) = ctrl(5)

  /** SrcA 0 拓展使能位
    *
    * @param ctrl
    * @return
    */
  def isAzero(ctrl: UInt) = ctrl(4)

  /** SrcB 0 拓展使能位
    *
    * @param ctrl
    * @return
    */
  def isBzero(ctrl: UInt) = ctrl(3)
}
