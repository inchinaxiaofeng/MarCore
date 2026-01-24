package core.isa.csr

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._

trait HasExceptionNO {
  def instrAddrMisaligned = 0
  def instrAccessFault = 1
  def illegalInstr = 2
  def breakPoint = 3
  def loadAddrMisaligned = 4
  def loadAccessFault = 5
  def storeAddrMisaligned = 6
  def storeAccessFault = 7
  def ecallU = 8
  def ecallS = 9
  def ecallM = 11
  def instrPageFault = 12
  def loadPageFault = 13
  def storePageFault = 15

  val ExcPriority = Seq(
    breakPoint,
    instrPageFault,
    instrAccessFault,
    illegalInstr,
    instrAddrMisaligned,
    ecallU,
    ecallS,
    ecallM,
    storeAddrMisaligned,
    storeAccessFault,
    storePageFault,
    loadAddrMisaligned,
    loadAccessFault,
    loadPageFault
  )
}
