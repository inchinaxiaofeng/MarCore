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

class RandCacheSpec
    extends AnyFreeSpec
    with Matchers
    with HasDebugPrint
    with HasMarCoreParameter {
  // 使用 1KB, 4 路, 32B 行
  implicit val cacheConfig: CacheConfig =
    CacheConfig(
      ro = false,
      name = "randcache",
      cacheSize = 1,
      ways = 4,
      lineSize = 32
    )

  "RandCache should forward a read miss to memory and return data" in {
    LogUtil.setDisplay(true)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    tracePrint = false
    simulate(new RandCache) { dut =>
      // ============ warm-up ============
      dut.io.in.req.valid.poke(false.B)
      dut.io.mem.req.ready.poke(true.B)
      dut.io.mem.resp.valid.poke(false.B)
      dut.io.mmio.req.ready.poke(true.B)
      dut.io.mmio.resp.valid.poke(false.B)
      dut.clock.step(5)

      // ============ miss 用例 ============
      val addr = 0x1000.U(PAddrBits.W)
      val size = "b10".U(2.W)
      val len = 0.U(3.W)
      val wdata = 0.U(DataBits.W)
      val isWrite = false.B

      // 1) 发请求到 S1
      dut.io.in.req.bits.addr.poke(addr)
      dut.io.in.req.bits.size.poke(size)
      dut.io.in.req.bits.len.poke(len)
      dut.io.in.req.bits.data.poke(wdata)
      dut.io.in.req.bits.write.poke(isWrite)
      dut.io.in.req.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.in.req.valid.poke(false.B)

      // 2) 多走几个周期，让它穿过 S1→S2→S3
      //    阶段数是 2 个 PipelineConnect + S3 自己的 Reg，所以至少3个 clock
      dut.clock.step(3)

      // 3) 这时应该驱动外部 memory 请求
      dut.io.mem.req.valid.peek().litToBoolean mustBe true
      dut.io.mem.req.bits.addr.peek().litValue mustBe addr.litValue

      // 4) 模拟 memory 返回
      val respData = 0xdeadbeefL.U(DataBits.W)
      dut.io.mem.resp.bits.data.poke(respData)
      dut.io.mem.resp.bits.last.poke(true.B)
      dut.io.mem.resp.bits.write.poke(false.B)
      dut.io.mem.resp.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.mem.resp.valid.poke(false.B)

      // 5) 再多给 S3 一个时钟，让它输出给 in.resp
      dut.clock.step(1)
      dut.io.in.resp.valid.peek().litToBoolean mustBe true
      dut.io.in.resp.bits.data.peek().litValue mustBe respData.litValue
    }
  }
  "RandCache should hit on subsequent read and not issue memory request" in {
    LogUtil.setDisplay(false)
    LogUtil.setLogLevel(LogLevel.TRACE)
    debugPrint = true
    tracePrint = false
    simulate(new RandCache) { dut =>
      // 初始化并 完成一次 miss fill
      dut.io.mem.req.ready.poke(true.B)
      dut.io.mem.resp.valid.poke(false.B)
      dut.io.mmio.req.ready.poke(true.B)
      dut.io.mmio.resp.valid.poke(false.B)
      dut.clock.step(5)

      val addr = 0x00002000.U(PAddrBits.W)
      val size = "b10".U(2.W)
      val len = 0.U(3.W)
      // miss path
      dut.io.in.req.bits.addr.poke(addr)
      dut.io.in.req.bits.size.poke(size)
      dut.io.in.req.bits.len.poke(len)
      dut.io.in.req.bits.write.poke(false.B)
      dut.io.in.req.valid.poke(true.B)
      dut.clock.step(3)
      dut.io.mem.req.ready.poke(true.B)
      dut.clock.step(1)
      val fillData = 0xfeedfaceL.U(DataBits.W)
      dut.io.mem.resp.bits.data.poke(fillData)
      dut.io.mem.resp.bits.last.poke(true.B)
      dut.io.mem.resp.bits.write.poke(false.B)
      dut.io.mem.resp.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.mem.resp.valid.poke(false.B)
      dut.clock.step(1)

      // 再次对同一地址读
      dut.io.in.req.bits.addr.poke(addr)
      dut.io.in.req.bits.size.poke(size)
      dut.io.in.req.bits.len.poke(len)
      dut.io.in.req.bits.write.poke(false.B)
      dut.io.in.req.valid.poke(true.B)
      dut.clock.step(1)
      dut.io.in.req.valid.poke(false.B)

      // 不应再发起 mem.req
      dut.io.mem.req.valid.peek().litToBoolean mustBe false
      // 直接从 cache 返回
      dut.clock.step(1)
      dut.io.in.resp.valid.peek().litToBoolean mustBe true
      // dut.io.in.resp.bits.data.peek().litToBoolean mustBe true
      dut.io.in.resp.bits.data.peek().litValue mustBe fillData.litValue
    }
  }
}
