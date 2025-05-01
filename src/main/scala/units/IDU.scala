package units

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config._
import defs._
import isa.riscv._
import module.fu._
import utils._

class IDU(implicit val p: MarCoreConfig) extends MarCoreModule {
  val io = IO(new Bundle {
    val in = Vec(2, Flipped(Decoupled(new CtrlFlowIO)))
    val out = Vec(2, Decoupled(new DecodeIO))
  })

  val (decoder1, decoder2) = BaseConfig.getT("ISA") match {
    case ISA.RISCV =>
      val d1 = Module(new RISCVDecoder)
      val d2 = Module(new RISCVDecoder)
      io.in(0) <> d1.io.in
      io.in(1) <> d2.io.in
      io.out(0) <> d1.io.out
      io.out(1) <> d2.io.out
      // 覆蓋
      if (!EnableMultiIssue) {
        io.in(1).ready := false.B
        d2.io.in.valid := false.B
      }
      io.out(0).bits.cf.isBranch := d1.io.isBranch
      (d1, d2)

    case ISA.MIPS =>
      val d1 = Module(new MIPSDecoder)
      val d2 = Module(new MIPSDecoder)
      io.in(0) <> d1.io.in
      io.in(1) <> d2.io.in
      io.out(0) <> d1.io.out
      io.out(1) <> d2.io.out
      // 覆蓋
      if (!EnableMultiIssue) {
        io.in(1).ready := false.B
        d2.io.in.valid := false.B
      }
      io.out(0).bits.cf.isBranch := d1.io.isBranch
      (d1, d2)

    case ISA.LoongArch =>
      val d1 = Module(new LoongArchDecoder)
      val d2 = Module(new LoongArchDecoder)
      io.in(0) <> d1.io.in
      io.in(1) <> d2.io.in
      io.out(0) <> d1.io.out
      io.out(1) <> d2.io.out
      // 覆蓋
      if (!EnableMultiIssue) {
        io.in(1).ready := false.B
        d2.io.in.valid := false.B
      }
      io.out(0).bits.cf.isBranch := d1.io.isBranch
      (d1, d2)
  }

  val checkpoint_id = RegInit(0.U(64.W))

  io.out(0).bits.cf.runahead_checkpoint_id := checkpoint_id
}
