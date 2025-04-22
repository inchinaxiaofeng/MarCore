package test.fu

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class MulUTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MulU"

  def genInput(signed: Boolean, XLEN: Int): BigInt = {
    val max = if (signed) BigInt(1) << (XLEN - 1) else BigInt(1) << XLEN
    val value = BigInt(XLEN, Random)
    if (signed && Random.nextBoolean()) value - max else value
  }

  it should "correctly handle random multiplication cases" in {
    test(new MulU) { c =>
      val XLEN = 64 // or 32 if you want to test 32-bit
      val N = 100

      for (_ <- 0 until N) {
        val a = genInput(signed = true, XLEN)
        val b = genInput(signed = true, XLEN)

        val ctrl = MulUCtrl.mul // MUL: signed x signed, low result

        c.io.in.valid.poke(true.B)
        c.io.in.bits.srcA.poke(a.U)
        c.io.in.bits.srcB.poke(b.U)
        c.io.in.bits.ctrl.poke(ctrl)
        c.clock.step()

        while (!c.io.out.valid.peek().litToBoolean) {
          c.clock.step()
        }

        val expected = (a * b) & ((BigInt(1) << XLEN) - 1)
        val got = c.io.out.bits.peek().litValue
        assert(
          got == expected,
          f"MUL failed: $a * $b = $expected%x but got $got%x"
        )
      }
    }
  }
}
