package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._

trait HasLoongArchCSRConst {
  val status = 0x000
}

class LoongArchCSR(implicit val p: MarCoreConfig)
    extends MarCoreCSRIOModule
    with HasLoongArchCSRConst
    with HasExceptionNO {
  implicit val moduleName: String = this.name
}
