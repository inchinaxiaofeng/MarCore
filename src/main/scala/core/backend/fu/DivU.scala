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
import core.uarch.fu.{DivUCtrl}

class DivU extends MarCoreFuModule {
  implicit val moduleName: String = this.name

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
