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
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import testutils._
import utils.{LogUtil, LogLevel}
import defs._

class BRUSpec
    extends AnyFreeSpec
    with Matchers
    with HasDebugPrint
    with HasMarCoreParameter {
  "BRU should correctly compute redirect for branch instructions" in {
    LogUtil.setDisplay(false)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    tracePrint = false
    simulate(new BRU) { dut =>
      // Warm-up
      dut.io.in.valid.poke(false.B)
      dut.io.cfIn.pc.poke(0.U)
      dut.io.cfIn.pnpc.poke(0.U)
      dut.io.in.bits.ctrl.poke(BRUCtrl.beq)
      dut.io.in.bits.srcA.poke(0.U)
      dut.io.in.bits.srcB.poke(0.U)
      dut.io.imm.poke(0.U)
      dut.clock.step(5)

      // Test vectors for branch opcodes and their golden taken condition
      val branchOps = Seq(
        BRUCtrl.beq -> ((a: BigInt, b: BigInt) => a == b),
        BRUCtrl.blt -> ((a: BigInt, b: BigInt) => {
          val sa =
            if ((a & (BigInt(1) << (XLEN - 1))) != 0) a - (BigInt(1) << XLEN)
            else a
          val sb =
            if ((b & (BigInt(1) << (XLEN - 1))) != 0) b - (BigInt(1) << XLEN)
            else b
          sa < sb
        }),
        BRUCtrl.bltu -> ((a: BigInt, b: BigInt) =>
          (a & ((BigInt(1) << XLEN) - 1)) < (b & ((BigInt(1) << XLEN) - 1))
        ),
        BRUCtrl.ble -> ((a: BigInt, b: BigInt) => {
          val sa =
            if ((a & (BigInt(1) << (XLEN - 1))) != 0) a - (BigInt(1) << XLEN)
            else a
          val sb =
            if ((b & (BigInt(1) << (XLEN - 1))) != 0) b - (BigInt(1) << XLEN)
            else b
          (sa < sb) || (sa == sb)
        })
      )

      val rnd = new scala.util.Random(99)
      val mask = (BigInt(1) << XLEN) - 1
      val pc = BigInt(0x100).setBit(0) & mask
      val imm = BigInt(8) & mask // branch offset

      branchOps.foreach { case (ctrlCode, takenFn) =>
        for (_ <- 0 until 100) {
          val a = BigInt(rnd.nextLong()) & mask
          val b = BigInt(rnd.nextLong()) & mask
          val taken = takenFn(a, b)

          // poke inputs
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.ctrl.poke(ctrlCode)
          dut.io.in.bits.srcA.poke(a.U)
          dut.io.in.bits.srcB.poke(b.U)
          dut.io.cfIn.pc.poke(pc.U)
          // assume predictor predicted fall-through (pc+4)
          val predNpc = (pc + 4) & mask
          dut.io.cfIn.pnpc.poke(predNpc.U)
          dut.io.imm.poke(imm.U)
          dut.io.out.ready.poke(true.B)
          dut.clock.step(1)

          // compute golden
          val directTarget = (pc + imm) & mask
          val fallThrough = predNpc
          val expectTarget = if (taken) directTarget else fallThrough
          val expectValid = taken // predictor was fall-through

          // Debug prints
          tprintln(
            s"[BRU Test] ctrl=0b${ctrlCode.litValue.toString(2).padTo(7, '0')}, a=0x${a
                .toString(16)}, b=0x${b.toString(16)}"
          )
          tprintln(s"          pc=0x${pc.toString(16)}, imm=0x${imm
              .toString(16)}, predNpc=0x${predNpc.toString(16)}")
          tprintln(
            s"          takenFn=$taken, expectValid=$expectValid, expectTarget=0x${expectTarget.toString(16)}"
          )
          val gotValid = dut.io.redirect.valid.peek().litToBoolean
          val gotTarget = dut.io.redirect.target.peek().litValue
          tprintln(
            s"          gotValid=$gotValid, gotTarget=0x${gotTarget.toString(16)}"
          )

          // check DUT
          withClue(s"Mismatch for ctrl=0b${ctrlCode.litValue
              .toString(2)}, a=0x${a.toString(16)}, b=0x${b.toString(16)}: ") {
            gotValid mustEqual expectValid
            gotTarget mustEqual expectTarget
          }
        }
      }
    }
  }
}
