package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._

trait HasLSUConst {
  val IndependentAddrCalcState = false
  val moqSize = 8
  val storeQueueSize = 8
}
