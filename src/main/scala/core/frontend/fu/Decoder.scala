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
package core.frontend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import utils.fu._

/** DecodeIO Bundle
  */
class DeIO extends MarCoreBundle {
  val in = Flipped(Decoupled(new CtrlFlowIO))
  val out = Decoupled(new DecodeIO)
  val isWFI = Output(Bool())
  val isBranch = Output(Bool())
}

/** 解码单元公共接口
  */
trait HasDeIO {
  val io = IO(new DeIO)
}

abstract class MarCoreDeModule extends MarCoreModule with HasDeIO;
