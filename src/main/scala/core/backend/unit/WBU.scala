package core.backend.unit

import chisel3._
import chisel3.util._

import defs._
import utils._
import core.uarch.interfaces._

class WBU(implicit val p: MarCoreConfig) extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CommitIO))
    val wb = new WriteBackIO
    val redirect = new RedirectIO
    // val difftest_commit =
    //   if (Settings.get("EnableDifftest")) Some(Decoupled(new CommitIO))
    //   else None
    // val difftest_instr =
    //   if (Settings.get("EnableDifftest")) Some(Output(UInt(XLEN.W))) else None
    // val difftest_redirect =
    //   if (Settings.get("EnableDifftest")) Some(new RedirectIO) else None
  })

  io.wb.rfWen := io.in.bits.decode.ctrl.rfWen && io.in.valid
  io.wb.rfDest := io.in.bits.decode.ctrl.rfDest
  io.wb.rfData := io.in.bits.commits(io.in.bits.decode.ctrl.fuType)

  io.in.ready := true.B

  io.redirect.target := io.in.bits.decode.cf.redirect.target
  io.redirect.rtype := io.in.bits.decode.cf.redirect.rtype
  io.redirect.valid := io.in.bits.decode.cf.redirect.valid && io.in.valid

  if (p.Log.LogWBU)
    Debug(
      io.in.valid,
      "[COMMIT] pc = 0x%x inst %x wen %x wdst %x wdata %x mmio %x intrNO %x\n",
      io.in.bits.decode.cf.pc,
      io.in.bits.decode.cf.instr,
      io.wb.rfWen,
      io.wb.rfDest,
      io.wb.rfData,
      io.in.bits.isMMIO,
      io.in.bits.intrNO
    )
}
