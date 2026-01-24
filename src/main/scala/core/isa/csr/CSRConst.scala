package core.isa.csr

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import core.uarch.fu.CSRCtrl

trait HasCSRConst {
  // Supervisor Protection and Translation
  val Satp = 0x180 // Supervisor Address Translation and Protection

  // Machine Trap Setup
  val Mstatus = 0x300
  val Mtvec = 0x305

  // Machine Trap Handing
  val Mepc = 0x341
  val Mcause = 0x342

  // Machine Counter Setup (not implemented)
  // Debug/Trace Register (shared with Debug Mode) (not implemented)
  // Debug Mode Register (not implemented)

  def privEcall = 0x000.U
  def privEbreak = 0x001.U
  def privMret = 0x302.U
  def privSret = 0x102.U
  def privUret = 0x002.U

  def ModeM = 0x3.U
  def ModeH = 0x2.U
  def ModeS = 0x1.U
  def ModeU = 0x0.U

  def IRQ_UEIP = 0
  def IRQ_SEIP = 1
  def IRQ_MEIP = 3

  def IRQ_UTIP = 4
  def IRQ_STIP = 5
  def IRQ_MTIP = 7

  def IRQ_USIP = 8
  def IRQ_SSIP = 9
  def IRQ_MSIP = 11

  val IntPriority = Seq(
    IRQ_MEIP,
    IRQ_MSIP,
    IRQ_MTIP,
    IRQ_SEIP,
    IRQ_SSIP,
    IRQ_STIP,
    IRQ_UEIP,
    IRQ_USIP,
    IRQ_UTIP
  )
}
