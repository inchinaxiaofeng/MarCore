package defs

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config.{ISAConfig, RV32E}

/** MarCore 设计参数:
  *
  * @note
  *   单次编译中会写死, 是权衡设计的一种.
  */
trait HasMarCoreParameter {
  val ISA = ISAConfig(baseType = RV32E)

  /** RF 相关 */
  val NRReg = ISA.numGPR
  val XLEN = ISA.xlen

  val AddrBits = XLEN // 芯片内使用
  val DataBits = XLEN
  val DataBytes = DataBits / 8

  val VAddrBits = XLEN // Based on 分页
  val PAddrBits = 32 // PAddrBits is Physical Memory address bits

  val HasICache = false
  val HasDCache = false
}
