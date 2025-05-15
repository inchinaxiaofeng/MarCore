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

class ALUSpec
    extends AnyFreeSpec
    with Matchers
    with HasDebugPrint
    with HasMarCoreParameter {
  "ALU should correctly implement all operations with random inputs" in {
    LogUtil.setDisplay(false)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    tracePrint = false

    simulate(new ALU) { dut =>
      // Reset & warm-up
      dut.io.in.valid.poke(false.B)
      dut.io.in.bits.srcA.poke(0.U)
      dut.io.in.bits.srcB.poke(0.U)
      dut.io.in.bits.ctrl.poke(ALUCtrl.add)
      dut.clock.step(5)

      // 通用的辅助
      val rnd = new scala.util.Random(42)
      val mask = (BigInt(1) << XLEN) - 1
      val mask32 = (BigInt(1) << 32) - 1
      // 移位
      val shamtBits = if (XLEN == 32) 5 else 6 // 32 位→5bit；64 位→6bit
      val shamtMask = (1 << shamtBits) - 1 // e.g. 0x1F 或 0x3F
      val shamtWMask = (1 << 5) - 1 // 子字操作永远 5bit
      dprintln(s"XLEN $XLEN")

      // golden-model functions for each ctrl code
      val ops = Seq(
        ALUCtrl.add.litValue -> ((a: BigInt, b: BigInt) => (a + b) & mask),
        ALUCtrl.addu.litValue -> ((a: BigInt, b: BigInt) => (a + b) & mask),
        ALUCtrl.sub.litValue -> ((a: BigInt, b: BigInt) => (a - b) & mask),
        ALUCtrl.addw.litValue -> ((a: BigInt, b: BigInt) => {
          val w = (a + b) & mask32
          val ext32 = if ((w & (BigInt(1) << 31)) != 0) w | (~mask32) else w
          ext32 & mask
        }),
        ALUCtrl.addwu.litValue -> ((a: BigInt, b: BigInt) => {
          val w = (a + b) & mask32
          w & mask
        }),
        ALUCtrl.subw.litValue -> ((a: BigInt, b: BigInt) => {
          val w = (a - b) & mask32
          val ext32 = if ((w & (BigInt(1) << 31)) != 0) w | (~mask32) else w
          ext32 & mask
        }),
        ALUCtrl.sll.litValue -> ((a: BigInt, b: BigInt) => {
          val amt = b.toInt & shamtMask
          ((a << amt) & mask)
        }),
        ALUCtrl.srl.litValue -> ((a: BigInt, b: BigInt) => {
          val amt = b.toInt & shamtMask
          (a >> amt) & mask
        }),
        ALUCtrl.sra.litValue -> ((a: BigInt, b: BigInt) => {
          val amt = b.toInt & shamtMask
          val signBit = BigInt(1) << (XLEN - 1)
          val signedA = if ((a & signBit) != 0) a - (BigInt(1) << XLEN) else a
          (signedA >> amt) & mask
        }),
        ALUCtrl.sllw.litValue -> ((a: BigInt, b: BigInt) => {
          val w = (a << (b.toInt & shamtWMask)) & mask32
          val ext = if ((w & (BigInt(1) << 31)) != 0) w | (~mask32) else w
          ext & mask
        }),
        ALUCtrl.srlw.litValue -> ((a: BigInt, b: BigInt) => {
          val amt = b.toInt & shamtWMask
          val w = (a >> amt) & mask32
          w & mask
        }),
        ALUCtrl.sraw.litValue -> ((a: BigInt, b: BigInt) => {
          val amt = b.toInt & shamtWMask
          val w32 = a & mask32
          // 把低 32 位当有符号数转回 BigInt
          val signed =
            if ((w32 & (BigInt(1) << 31)) != 0) w32 - (BigInt(1) << 32) else w32
          val resW = (signed >> amt) & mask32
          // 符号扩展到 full-width
          val ext =
            if ((resW & (BigInt(1) << 31)) != 0) resW | (~mask32) else resW
          ext & mask
        }),
        ALUCtrl.slt.litValue -> ((a: BigInt, b: BigInt) => {
          val signA =
            if ((a & (BigInt(1) << (XLEN - 1))) != 0) a - (BigInt(1) << XLEN)
            else a
          val signB =
            if ((b & (BigInt(1) << (XLEN - 1))) != 0) b - (BigInt(1) << XLEN)
            else b
          if (signA < signB) BigInt(1) else BigInt(0)
        }),
        ALUCtrl.sltu.litValue -> ((a: BigInt, b: BigInt) =>
          if ((a & mask) < (b & mask)) BigInt(1) else BigInt(0)
        ),
        ALUCtrl.or.litValue -> ((a: BigInt, b: BigInt) => (a | b) & mask),
        ALUCtrl.and.litValue -> ((a: BigInt, b: BigInt) => (a & b) & mask),
        ALUCtrl.nor.litValue -> ((a: BigInt, b: BigInt) => (~(a | b)) & mask),
        ALUCtrl.xor.litValue -> ((a: BigInt, b: BigInt) => (a ^ b) & mask)
      )

      // run each op 1000 times with random inputs
      ops.foreach { case (ctrlCode, golden) =>
        for (_ <- 0 until 1000) {
          val a = BigInt(rnd.nextLong()) & mask
          val b = BigInt(rnd.nextLong()) & mask

          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.srcA.poke(a.U)
          dut.io.in.bits.srcB.poke(b.U)
          dut.io.in.bits.ctrl.poke(ctrlCode.U)
          dut.io.out.ready.poke(true.B)
          dut.clock.step(1)

          val got = dut.io.out.bits.peek().litValue & mask
          val expect = golden(a, b).asInstanceOf[BigInt]
          assert(
            got == expect,
            s"ALU mismatch: ctrl=0b${ctrlCode.toString(2)}, a=0x${a
                .toString(16)}, b=0x${b.toString(16)}, got=0x${got
                .toString(16)}, expect=0x${expect.toString(16)}"
          )
        }
      }
    }
  }
}
