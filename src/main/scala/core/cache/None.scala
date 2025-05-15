package core.cache

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.cacheBus._
import config.BaseConfig

private[cache] class NoneCache(implicit val cacheConfig: CacheConfig)
    extends CacheModule {
  implicit val moduleName: String = this.name
  val s_idle :: s_memReq :: s_memResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: Nil =
    Enum(6)
  val state = RegInit(s_idle)

  val ismmio = AddressSpace.isMMIO(io.in.req.bits.addr)
  val ismmioRec = RegEnable(ismmio, io.in.req.fire)
  // if (cacheConfig.name == "dacache") {
  //   BoringUtils.addSource(WireInit(ismmio), "lsumMMIO")
  // }

  val needFlush = RegInit(false.B)
  when(io.flush(0) && (state =/= s_idle)) { needFlush := true.B }
  when(state === s_idle && needFlush) { needFlush := false.B }

  val alreadyOutFire = RegEnable(true.B, false.B, io.in.resp.fire)

  switch(state) {
    is(s_idle) {
      alreadyOutFire := false.B
      when(io.in.req.fire && !io.flush(0)) {
        state := Mux(ismmio, s_mmioReq, s_memReq)
      }
    }
    is(s_memReq) { when(io.mem.req.fire) { state := s_memResp } }
    is(s_memResp) { when(io.mem.resp.fire) { state := s_wait_resp } }
    is(s_mmioReq) { when(io.mmio.req.fire) { state := s_mmioResp } }
    is(s_mmioResp) {
      when(io.mmio.resp.fire || alreadyOutFire) { state := s_wait_resp }
    }
    is(s_wait_resp) {
      when(io.in.resp.fire || needFlush || alreadyOutFire) { state := s_idle }
    }
  }

  val reqAddr = RegEnable(io.in.req.bits.addr, io.in.req.fire)
  val reqLen = RegEnable(io.in.req.bits.len, io.in.req.fire)
  val reqSize = RegEnable(io.in.req.bits.size, io.in.req.fire)
  val reqData = RegEnable(io.in.req.bits.data, io.in.req.fire)
  val reqStrb = RegEnable(io.in.req.bits.strb, io.in.req.fire)
  val reqLast = RegEnable(io.in.req.bits.last, io.in.req.fire)
  val reqWrite = RegEnable(io.in.req.bits.write, io.in.req.fire)

  io.in.req.ready := (state === s_idle)
  io.in.resp.valid := (state === s_wait_resp) && (!needFlush)

  val mmioData = RegEnable(io.mmio.resp.bits.data, io.mmio.resp.fire)
  val mmioLast = RegEnable(io.mmio.resp.bits.last, io.mmio.resp.fire)
  val mmioWrite = RegEnable(io.mmio.resp.bits.write, io.mmio.resp.fire)
  val memData = RegEnable(io.mem.resp.bits.data, io.mem.resp.fire)
  val memLast = RegEnable(io.mem.resp.bits.last, io.mem.resp.fire)
  val memWrite = RegEnable(io.mem.resp.bits.write, io.mem.resp.fire)

  io.in.resp.bits.data := Mux(ismmioRec, mmioData, memData)
  io.in.resp.bits.last := Mux(ismmioRec, mmioLast, memLast)
  io.in.resp.bits.write := Mux(ismmioRec, mmioWrite, memWrite)

  val memUser = RegEnable(io.in.req.bits.user.getOrElse(0.U), io.in.req.fire)
  io.in.resp.bits.user.zip(if (userBits > 0) Some(memUser) else None).map {
    case (o, i) => o := i
  }

  io.mem.req.bits.apply(
    addr = reqAddr,
    size = reqSize,
    data = reqData,
    strb = reqStrb,
    last = reqLast,
    write = reqWrite,
    len = reqLen
  )
  io.mem.req.valid := (state === s_memReq)
  io.mem.resp.ready := true.B

  io.mmio.req.bits.apply(
    addr = reqAddr,
    size = reqSize,
    data = reqData,
    strb = reqStrb,
    last = reqLast,
    write = reqWrite,
    len = reqLen
  )
  io.mmio.req.valid := (state === s_mmioReq)
  io.mmio.resp.ready := true.B

  io.empty := false.B

  if (BaseConfig.get("LogCache")) {
    Debug(io.in.req.fire, p"in.req: ${io.in.req.bits}\n")
    Debug(io.mem.req.fire, p"mem.req: ${io.mem.req.bits}\n")
    Debug(io.mem.resp.fire, p"mem.resp: ${io.mem.resp.bits}\n")
    Debug(io.in.resp.fire, p"in.resp: ${io.in.resp.bits}\n")
  }
}
