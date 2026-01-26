package core.frontend.unit

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config._

import defs._
import utils._
import core.frontend.fu._
import core.uarch.interfaces.{CtrlFlowIO, DecodeIO}

class IDU(implicit val p: MarCoreConfig) extends MarCoreModule {
  val io = IO(new Bundle {
    val in = Vec(2, Flipped(Decoupled(new CtrlFlowIO)))
    val out = Vec(2, Decoupled(new DecodeIO))
  })

  val (decoder1, decoder2) = (Module(new Decoder), Module(new Decoder))
  io.in(0) <> decoder1.io.in
  io.in(1) <> decoder2.io.in
  io.out(0) <> decoder1.io.out
  io.out(1) <> decoder2.io.out
  // 覆蓋
  if (!p.Core.EnableMultiIssue) {
    io.in(1).ready := false.B
    decoder2.io.in.valid := false.B
  }
  io.out(0).bits.cf.isBranch := decoder1.io.isBranch

  val checkpoint_id = RegInit(0.U(64.W))

  io.out(0).bits.cf.runahead_checkpoint_id := checkpoint_id
}
