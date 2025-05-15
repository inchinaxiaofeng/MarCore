/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import utils.fu._
import top.Settings
import config.BaseConfig

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

class MulU extends MarCoreFuModule {
  implicit val moduleName: String = this.name

  val isHigh = MulUCtrl.isHigh(ctrl)
  val isW = MulUCtrl.isW(ctrl)
  val isAzero = MulUCtrl.isAzero(ctrl)
  val isBzero = MulUCtrl.isBzero(ctrl)

  val srcAsign = SignExt(srcA, XLEN + 1)
  val srcAzero = ZeroExt(srcA, XLEN + 1)
  val srcBsign = SignExt(srcB, XLEN + 1)
  val srcBzero = ZeroExt(srcB, XLEN + 1)

  /* --- 下面是邏輯實現 --- */

  // pipeline valid 控制信号
  val s0_valid = RegInit(false.B)
  val s1_valid = RegInit(false.B)
  val s2_valid = RegInit(false.B)

  // pipeline 逻辑
  when(io.in.valid) {
    s0_valid := true.B
  }.elsewhen(s0_valid) {
    s0_valid := false.B
  }

  when(s0_valid) {
    s1_valid := true.B
  }.elsewhen(s1_valid) {
    s1_valid := false.B
  }

  when(s1_valid) {
    s2_valid := true.B
  }.elsewhen(s2_valid) {
    s2_valid := false.B
  }

  // Instantiate ArrayMulDataModule
  val mul = Module(new ArrayMulDataModule(XLEN + 1))
  mul.io.a := Mux(isAzero, srcAzero, srcAsign)
  mul.io.b := Mux(isBzero, srcBzero, srcBsign)

  mul.io.regEnables(0) := s0_valid
  mul.io.regEnables(1) := s1_valid

  // 高/低结果选择
  val res = Mux(
    isHigh,
    mul.io.result(2 * XLEN - 1, XLEN),
    mul.io.result(XLEN - 1, 0)
  )

  io.out.bits := Mux(isW, SignExt(res(31, 0), XLEN), res)
  io.in.ready := !s0_valid // ready 条件：空闲时才 ready
  io.out.valid := s2_valid

  // ==== LogOut ====
  if (BaseConfig.get("LogMulU")) {
    Trace(s"stage valid $s0_valid$s1_valid$s2_valid\n")
    Debug(
      io.in.fire,
      "[In  Fire] ctrl 0b%b srcA 0x%x srcB 0x%x\n",
      ctrl,
      srcA,
      srcB
    )
    Debug(
      io.out.fire,
      "[Out Fire] out 0x%x\n",
      io.out.bits
    )
  }
}
