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

import core.uarch.fu.MulUCtrl

class MulU(implicit val p: MarCoreConfig) extends MarCoreFuModule {
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
  if (p.Log.LogMulU) {
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
