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
package core.cache

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import testutils._
import utils.{LogUtil, LogLevel}
import defs._
import bus.cacheBus._

class NoneCacheSpec
    extends AnyFreeSpec
    with Matchers
    with HasDebugPrint
    with HasMarCoreParameter {
  implicit val cacheConfig: CacheConfig =
    CacheConfig(ro = false, name = "nonecache", cacheSize = 1)

  "NoneCache should forward a read request to memory and return data" in {
    LogUtil.setDisplay(false)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    simulate(new NoneCache) { dut =>
      // warm-up
      dut.io.in.req.valid.poke(false.B)
      dut.io.mem.req.ready.poke(true.B)
      dut.io.mem.resp.valid.poke(false.B)
      dut.io.mmio.req.ready.poke(true.B)
      dut.io.mmio.resp.valid.poke(false.B)
      dut.clock.step(5)

      // 1. Drive a read request to non-MMIO address 0x1000
      val addr = 0x1000.U(PAddrBits.W)
      val size = "b10".U(2.W) // word sized 32-bit
      val len = 0.U(3.W) // single beat
      // poke request
      dut.io.in.req.bits.addr.poke(addr)
      dut.io.in.req.bits.size.poke(size)
      dut.io.in.req.bits.len.poke(len)
      dut.io.in.req.bits.write.poke(false.B)
      dut.io.in.req.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.in.req.valid.poke(false.B)

      // should issue memory request
      dut.io.mem.req.valid.peek().litToBoolean mustBe true
      dut.io.mem.req.bits.addr.peek().litValue mustBe addr.litValue
      // complete mem.req handshake
      dut.io.mem.req.ready.poke(true.B)
      dut.clock.step(1)

      // drive memory response
      val respData = 0xdeadbeefL.U(DataBits.W)
      dut.io.mem.resp.bits.data.poke(respData)
      dut.io.mem.resp.bits.last.poke(true.B)
      dut.io.mem.resp.bits.write.poke(false.B)
      dut.io.mem.resp.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.mem.resp.valid.poke(false.B)

      // now in resp should be valid
      dut.io.in.resp.valid.peek().litToBoolean mustBe true
      dut.io.in.resp.bits.data.peek().litValue mustBe respData.litValue
    }
  }

  "NoneCache should forward a write request to MMIO and return written data" in {
    LogUtil.setDisplay(false)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    simulate(new NoneCache) { dut =>
      // warm-up
      dut.io.in.req.valid.poke(false.B)
      dut.io.mem.req.ready.poke(true.B)
      dut.io.mmio.req.ready.poke(true.B)
      dut.io.mmio.resp.valid.poke(false.B)
      dut.clock.step(5)

      // 2. Drive a write request to MMIO address 0x80000000
      val mmAddr = "h80000000".U(PAddrBits.W)
      val wdata = 0x12345678.U(DataBits.W)
      val size = "b10".U(2.W)
      val len = 0.U(3.W)
      dut.io.in.req.bits.addr.poke(mmAddr)
      dut.io.in.req.bits.data.poke(wdata)
      dut.io.in.req.bits.write.poke(true.B)
      dut.io.in.req.bits.size.poke(size)
      dut.io.in.req.bits.len.poke(len)
      dut.io.in.req.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.in.req.valid.poke(false.B)

      // should issue MMIO request
      dut.io.mmio.req.valid.peek().litToBoolean mustBe true
      dut.io.mmio.req.bits.addr.peek().litValue mustBe mmAddr.litValue
      dut.io.mmio.req.bits.data.peek().litValue mustBe wdata.litValue
      // handshake
      dut.io.mmio.req.ready.poke(true.B)
      dut.clock.step(1)

      // mock MMIO response echoes back data
      dut.io.mmio.resp.bits.data.poke(wdata)
      dut.io.mmio.resp.bits.last.poke(true.B)
      dut.io.mmio.resp.bits.write.poke(true.B)
      dut.io.mmio.resp.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.mmio.resp.valid.poke(false.B)

      // check in.resp
      dut.io.in.resp.valid.peek().litToBoolean mustBe true
      dut.io.in.resp.bits.data.peek().litValue mustBe wdata.litValue
      dut.io.in.resp.bits.write.peek().litToBoolean mustBe true
    }
  }
}
