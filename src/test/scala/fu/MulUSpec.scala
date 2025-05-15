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

package module.fu

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import testutils._
import utils.{LogUtil, LogLevel}
import defs._
import module.fu.MulU

class MulUSpec
    extends AnyFreeSpec
    with Matchers
    with HasDebugPrint
    with HasMarCoreParameter {
  "MulU should implement signed/unsigned and high/low multiplications" in {
    LogUtil.setDisplay(false)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    tracePrint = false
    simulate(new MulU) { dut =>
      // reset and warm-up pipeline
      dut.io.in.valid.poke(false.B)
      dut.io.out.ready.poke(false.B)
      dut.clock.step(5)

      val rnd = new scala.util.Random(123)
      val maskXL = (BigInt(1) << XLEN) - 1
      val mask32 = (BigInt(1) << 32) - 1

      // build golden-model functions
      val ops = Seq(
        MulUCtrl.mul.litValue -> { (a: BigInt, b: BigInt) =>
          // unsigned full
          val prod = (a & maskXL) * (b & maskXL)
          prod & maskXL
        },
        MulUCtrl.mulh.litValue -> { (a: BigInt, b: BigInt) =>
          // signed full->high
          val sa =
            if ((a & (BigInt(1) << (XLEN - 1))) != 0) a - (BigInt(1) << XLEN)
            else a
          val sb =
            if ((b & (BigInt(1) << (XLEN - 1))) != 0) b - (BigInt(1) << XLEN)
            else b
          val prod = sa * sb
          ((prod >> XLEN) & maskXL)
        },
        MulUCtrl.mulhu.litValue -> { (a: BigInt, b: BigInt) =>
          // unsigned full->high
          val prod = (a & maskXL) * (b & maskXL)
          ((prod >> XLEN) & maskXL)
        },
        MulUCtrl.mulhsu.litValue -> { (a: BigInt, b: BigInt) =>
          // signed a * unsigned b -> high
          val sa =
            if ((a & (BigInt(1) << (XLEN - 1))) != 0) a - (BigInt(1) << XLEN)
            else a
          val prod = sa * (b & maskXL)
          ((prod >> XLEN) & maskXL)
        },
        MulUCtrl.mulw.litValue -> { (a: BigInt, b: BigInt) =>
          // unsigned low32, sign-extend
          val prod = (a & maskXL) * (b & maskXL)
          val low32 = (prod & mask32)
          // sign-extend 32->XLEN
          if ((low32 & (BigInt(1) << 31)) != 0) (low32 | (~mask32)) & maskXL
          else low32 & maskXL
        },
        MulUCtrl.mulhw.litValue -> { (a: BigInt, b: BigInt) =>
          // signed full->high, then low32 word
          val sa =
            if ((a & (BigInt(1) << (XLEN - 1))) != 0) a - (BigInt(1) << XLEN)
            else a
          val sb =
            if ((b & (BigInt(1) << (XLEN - 1))) != 0) b - (BigInt(1) << XLEN)
            else b
          val prod = sa * sb
          val hi = (prod >> XLEN) & maskXL
          // low 32 bits of hi, sign-extend
          val w32 = hi & mask32
          if ((w32 & (BigInt(1) << 31)) != 0) (w32 | (~mask32)) & maskXL
          else w32 & maskXL
        },
        MulUCtrl.mulhwu.litValue -> { (a: BigInt, b: BigInt) =>
          // unsigned full->high->word zero-extend
          val prod = (a & maskXL) * (b & maskXL)
          val hi = (prod >> XLEN) & maskXL
          hi & mask32
        }
      )

      // run vectors
      ops.foreach { case (ctrlCode, golden) =>
        for (_ <- 0 until 100) {
          val a = BigInt(rnd.nextLong()) & maskXL
          val b = BigInt(rnd.nextLong()) & maskXL
          val expect = golden(a, b)

          // poke inputs
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.srcA.poke(a.U)
          dut.io.in.bits.srcB.poke(b.U)
          dut.io.in.bits.ctrl.poke(ctrlCode)
          dut.io.out.ready.poke(true.B)
          // step pipeline 3 stages
          dut.clock.step(3)

          // check
          dut.io.out.valid.peek().litToBoolean mustBe true
          val got = dut.io.out.bits.peek().litValue
          withClue(
            s"MulU ctrl=0b${ctrlCode.toString(2)}, a=0x${a
                .toString(16)}, b=0x${b.toString(16)}: "
          ) {
            got mustEqual expect
          }
        }
      }
    }
  }
}
