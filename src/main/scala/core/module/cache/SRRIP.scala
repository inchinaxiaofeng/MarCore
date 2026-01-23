package module.cache

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import top.Settings
import bus.cacheBus._
import bus.debugBus._
import module.cache._
import blackbox._

sealed class SRRIPMetaBundle(ageBits: Int)(implicit cacheConfig: CacheConfig)
    extends MetaBundle {
  val rrpv = Output(UInt(ageBits.W)) // which is age

  def apply(
      tag: UInt = 0.U,
      valid: Bool = false.B,
      dirty: Bool = false.B,
      rrpv: UInt = 0.U
  ) = {
    super.apply(tag = tag, valid = valid, dirty = dirty)
    this.rrpv := rrpv
    this
  }
}

sealed trait HasSRRIPConst extends HasCacheConst {
  // implicit val cacheConfig: CacheConfig
  def ageBits = 2
  def maxRRPV: UInt = ((1 << ageBits) - 1).U(ageBits.W)
  def CacheMetaArrayReadBus() = new SRAMReadBus(
    new SRRIPMetaBundle(ageBits = ageBits),
    set = Sets,
    way = Ways
  )
  def CacheMetaArrayWriteBus() = new SRAMWriteBus(
    new SRRIPMetaBundle(ageBits = ageBits),
    set = Sets,
    way = Ways
  )
  def CacheMetaArrayFlushBus() =
    new SRAMFlushBus(new SRRIPMetaBundle(ageBits = ageBits), way = Ways)
  def CacheDataArrayReadBus() =
    new SRAMReadBus(new DataBundle, set = Sets * LineBeats, way = Ways)
  def CacheDataArrayWriteBus() =
    new SRAMWriteBus(new DataBundle, set = Sets * LineBeats, way = Ways)
}

sealed class SRRIPStage1IO(implicit val cacheConfig: CacheConfig)
    extends CacheBundle {
  val req = new CacheBusReqBundle(userBits = userBits, idBits = idBits)
}

sealed class SRRIPCacheStage1(implicit val cacheConfig: CacheConfig)
    extends CacheModule
    with HasSRRIPConst {
  implicit val moduleName: String = cacheName
  class CacheStage1IO extends Bundle {
    val in = Flipped(
      Decoupled(new CacheBusReqBundle(userBits = userBits, idBits = idBits))
    )
    val out = Decoupled(new SRRIPStage1IO)
    val metaReadBus = CacheMetaArrayReadBus()
    val dataReadBus = CacheDataArrayReadBus()
  }
  val io = IO(new CacheStage1IO)

  if (ro) when(io.in.fire) { assert(!io.in.bits.write) }
  Debug(
    io.in.fire,
    "[L1$] Cache stage 1, addr in: %x, user: %x, id: %x\n",
    io.in.bits.addr,
    io.in.bits.user.getOrElse(3053.U),
    io.in.bits.id.getOrElse(3053.U)
  )

  // Read meta and data
  val readBusValid = io.in.valid && io.out.ready
  io.metaReadBus.apply(
    valid = readBusValid,
    setIdx = getMetaIdx(io.in.bits.addr)
  )
  io.dataReadBus.apply(
    valid = readBusValid,
    setIdx = getDataIdx(io.in.bits.addr)
  )

  io.out.bits.req := io.in.bits
  io.out.valid := io.in.valid && io.metaReadBus.req.ready && io.dataReadBus.req.ready
  io.in.ready := (!io.in.valid || io.out.fire) && io.metaReadBus.req.ready && io.dataReadBus.req.ready
}

sealed class SRRIPStage2IO(implicit val cacheConfig: CacheConfig)
    extends CacheBundle
    with HasSRRIPConst {
  val req = new CacheBusReqBundle(userBits = userBits, idBits = idBits)
  val metas = Vec(Ways, new SRRIPMetaBundle(ageBits))
  val datas = Vec(Ways, new DataBundle)
  val hit = Output(Bool())
  val waymask = Output(UInt(Ways.W))
  val mmio = Output(Bool())
  val isForwardData = Output(Bool())
  val forwardData = Output(CacheDataArrayWriteBus().req.bits)
  val crtMaxRRPV = Output(UInt(ageBits.W))
  val flushWaymask = Output(UInt(Ways.W))
}

sealed class SRRIPCacheStage2(implicit val cacheConfig: CacheConfig)
    extends CacheModule
    with HasSRRIPConst {
  implicit val moduleName: String = cacheName
  class CacheStage2IO extends Bundle {
    val in = Flipped(Decoupled(new SRRIPStage1IO))
    val out = Decoupled(new SRRIPStage2IO)
    val metaReadResp = Flipped(
      Vec(Ways, new SRRIPMetaBundle(ageBits = ageBits))
    )
    val dataReadResp = Flipped(Vec(Ways, new DataBundle))
    val metaWriteBus = Input(CacheMetaArrayWriteBus()) // Refill
    val dataWriteBus = Input(CacheDataArrayWriteBus()) // Refill
    val forwardRRPVBios = Input(UInt(ageBits.W))
  }
  val io = IO(new CacheStage2IO)

  val req = io.in.bits.req
  val addr = req.addr.asTypeOf(addrBundle)

  // 写回元数据，由第三流水级写回
  val isForwardMeta =
    io.in.valid && io.metaWriteBus.req.valid && io.metaWriteBus.req.bits.setIdx === getMetaIdx(
      req.addr
    )
  val isForwardMetaReg = RegInit(false.B)
  val forwardRRPVBiosReg = RegInit(0.U)
  when(isForwardMeta) {
    isForwardMetaReg := true.B; forwardRRPVBiosReg := io.forwardRRPVBios
  }
  when(io.in.fire || !io.in.valid) {
    isForwardMetaReg := false.B; forwardRRPVBiosReg := 0.U
  }
  val forwardMetaReg = RegEnable(
    io.metaWriteBus.req.bits,
    isForwardMeta
  ) // _.data = MetaBundle, _.waymask = Some(Output(UInt(Ways.W))) or None

  /* 对MetaWay进行驱动(Vec(Ways, MetaBundle)) */
  val metaWay = Wire(
    Vec(Ways, chiselTypeOf(forwardMetaReg.data))
  ) // Vec(Ways, MetaBundle)
  val pickForwardMeta = isForwardMetaReg || isForwardMeta
  val forwardMeta = Mux(
    isForwardMeta,
    io.metaWriteBus.req.bits,
    forwardMetaReg
  ) // Forwarding, 直接指向当前的Meta，否则指向暂存的Meta
  val forwardBios =
    Mux(isForwardMeta, io.forwardRRPVBios, forwardRRPVBiosReg) // Bios the RRPV
  val forwardWayMask =
    forwardMeta.waymask
      .getOrElse("1".U)
      .asBools // 转换为Bool值序列，指出当前对应的Meta是哪个way有效
  forwardWayMask.zipWithIndex.map {
    case (w, i) => {
      val updateMetaReadResp = Wire(io.metaReadResp(i).cloneType)
      updateMetaReadResp := io.metaReadResp(i)
      updateMetaReadResp.rrpv := io.metaReadResp(i).rrpv + forwardBios
      metaWay(i) := Mux(
        pickForwardMeta && w,
        forwardMeta.data,
        updateMetaReadResp
      )
//		metaWay(i) := Mux(pickForwardMeta && w, forwardMeta.data, io.metaReadResp(i))
    }
  } // 当使用Forward数据时，将Forward的某一个数据写入对应的way中（由forwardWayMask序列决定），其余的由前级读出数据驱动；不使用时，则全部驱动

  /* Chose Victim line */
  // Fixme maybe
  val victimLine = metaWay.reduceLeft((x, y) => Mux(x.rrpv > y.rrpv, x, y))
  val crtMaxRRPV = victimLine.rrpv
  val victimIdx = metaWay.indexWhere(_.rrpv === crtMaxRRPV).asUInt

  /* 驱动命中向量信号 */
  val hitVec = VecInit(
    metaWay.map(m => m.valid && (m.tag === addr.tag) && io.in.valid)
  ).asUInt
  val victimWaymask = if (Ways > 1) (1.U << victimIdx) else "b1".U

  /* 找到非法值行 */
  val invalidVec = VecInit(metaWay.map(m => !m.valid)).asUInt
  val hasInvalidWay = invalidVec.orR // 是否存在非法行
  val refillInvalidWaymask = UIntToOH(PriorityEncoder(invalidVec), Ways)

  /* 路掩码 */
  val waymask = Mux(
    io.out.bits.hit,
    hitVec,
    Mux(hasInvalidWay, refillInvalidWaymask, victimWaymask)
  ) // Fixme: HitVec
  when(PopCount(waymask) > 1.U) {
    metaWay.map(m =>
      Debug("[ERROR] metaWay %x metat %x reqt %x\n", m.valid, m.tag, addr.tag)
    )
  }
  when(PopCount(waymask) > 1.U) {
    Debug(
      "[ERROR] hit %b wmask %b hitvec %b\n",
      io.out.bits.hit,
      forwardMeta.waymask.getOrElse("1".U),
      hitVec
    )
  }
  assert(!(io.in.valid && PopCount(waymask) > 1.U))

  io.out.bits.metas := metaWay
  io.out.bits.crtMaxRRPV := crtMaxRRPV
  io.out.bits.flushWaymask := (~invalidVec) & (~waymask)
  io.out.bits.hit := io.in.valid && hitVec.orR
  io.out.bits.waymask := waymask
  io.out.bits.datas := io.dataReadResp // Fixme: 在这里可能给错了值，没有正确的给出高八个的值
  io.out.bits.mmio := AddressSpace.isMMIO(req.addr)

  Debug("Hit or Miss: %d\n", io.out.bits.hit)

  // 写回数据，由第三流水级写回
  val isForwardData = io.in.valid && (io.dataWriteBus.req match {
    case r => r.valid && r.bits.setIdx === getDataIdx(req.addr)
  }) // 一个Line中有多个可以寻找的量
  val isForwardDataReg = RegInit(false.B)
  when(isForwardData) { isForwardDataReg := true.B }
  when(io.in.fire || !io.in.valid) { isForwardDataReg := false.B }
  val forwardDataReg = RegEnable(io.dataWriteBus.req.bits, isForwardData)
  io.out.bits.isForwardData := isForwardDataReg || isForwardData
  io.out.bits.forwardData := Mux(
    isForwardData,
    io.dataWriteBus.req.bits,
    forwardDataReg
  )

  io.out.bits.req <> req
  io.out.valid := io.in.valid
  io.in.ready := !io.in.valid || io.out.fire
  if (Settings.get("TraceCache")) {
    Debug(
      "[isFD:%d isFDreg:%d inFire:%d invalid:%d\n",
      isForwardData,
      isForwardDataReg,
      io.in.fire,
      io.in.valid
    )
    Debug(
      "[isFM:%d isFMreg:%d metawreq:%x widx:%x ridx:%x\n",
      isForwardMeta,
      isForwardMetaReg,
      io.metaWriteBus.req.valid,
      io.metaWriteBus.req.bits.setIdx,
      getMetaIdx(req.addr)
    )
  }
}

// Write Back
sealed class SRRIPCacheStage3(implicit val cacheConfig: CacheConfig)
    extends CacheModule
    with HasSRRIPConst {
  implicit val moduleName: String = cacheName
  class CacheStage3IO extends Bundle {
    val in = Flipped(Decoupled(new SRRIPStage2IO))
    val out = Decoupled(
      new CacheBusRespBundle(userBits = userBits, idBits = idBits)
    )
    val isFinish = Output(Bool())
    val flush = Input(Bool())
    val dataReadBus = CacheDataArrayReadBus()
    val dataWriteBus = CacheDataArrayWriteBus()
    val metaWriteBus = CacheMetaArrayWriteBus()

    val mem = new CacheBus
    val mmio = new CacheBus
    val flushBus = CacheMetaArrayFlushBus()

    // use to distinguish prefetch request and normal request
    val dataReadRespToL1 = Output(Bool())
    val forwardRRPVBios = Output(UInt(ageBits.W))
  }
  val io = IO(new CacheStage3IO)

  val metaWriteArb = Module(new Arbiter(CacheMetaArrayWriteBus().req.bits, 2))
  val dataWriteArb = Module(new Arbiter(CacheDataArrayWriteBus().req.bits, 2))

  // rename the wire
  val req = io.in.bits.req
  val addr = req.addr.asTypeOf(addrBundle)
  val mmio = io.in.valid && io.in.bits.mmio
  val hit = io.in.valid && io.in.bits.hit
  val miss = io.in.valid && !io.in.bits.hit
  val hitReadBurst = hit && !req.write && req.len =/= 1.U
  val meta = Mux1H(io.in.bits.waymask, io.in.bits.metas) // 一个Meta Bundle
  assert(!(mmio && hit), "MMIO request should not hit in cache")

  val statistic_cache = Module(new STATISTIC_CACHE())
  statistic_cache.io.clk := clock
  statistic_cache.io.rst := reset
  statistic_cache.io.stat := Cat(hit, miss)
  if (cacheName == "icache")
    statistic_cache.io.id := 1.U
  else if (cacheName == "dcache")
    statistic_cache.io.id := 2.U
  else
    statistic_cache.io.id := 0.U

  if (cacheName == "dcache") {
    BoringUtils.addSource(WireInit(mmio), "lsuMMIO")
  }

  /* 驱动数据线，驱动字掩码 */
  val useForwardData =
    io.in.bits.isForwardData && io.in.bits.waymask === io.in.bits.forwardData.waymask
      .getOrElse("b1".U)
  val dataReadArray =
    Mux1H(
      io.in.bits.waymask,
      io.in.bits.datas
    ).data // FIXME: Cant't read correct value, 0 is wrong.
//	Debug("waymask %b data0 %x data1 %x data2 %x data3 %x\n", io.in.bits.waymask, io.in.bits.datas(0).data, io.in.bits.datas(1).data, io.in.bits.datas(2).data, io.in.bits.datas(3).data)
//	Debug("useForwardData %d data %x dataReadArray %x\n", useForwardData, io.in.bits.forwardData.data.data, dataReadArray)
  val dataRead =
    Mux(useForwardData, io.in.bits.forwardData.data.data, dataReadArray)
  val wordMask = Mux(
    !ro.B && req.write,
    MaskExpend(req.strb),
    0.U(DataBits.W)
  ) // 得到供MaskData需要的以字为粒度的位掩码

  val writeL2BeatCnt = Counter(LineBeats) // 用于计数扫描，按拍长写入
  when(io.out.fire && req.write && req.len =/= 1.U) {
    writeL2BeatCnt.inc()
  }

  val hitWrite = hit && req.write
  val dataHitWriteBus = Wire(CacheDataArrayWriteBus()).apply(
    valid = hitWrite,
    setIdx = Cat(
      addr.index,
      Mux(req.write && req.len =/= 1.U, writeL2BeatCnt.value, addr.wordIndex)
    ),
    waymask = io.in.bits.waymask,
    data = Wire(new DataBundle).apply(MaskData(dataRead, req.data, wordMask))
  )
  val metaHitWriteBus = Wire(CacheMetaArrayWriteBus()).apply(
    valid = hitWrite && !meta.dirty,
    setIdx = getMetaIdx(req.addr),
    waymask = io.in.bits.waymask,
    data = Wire(new SRRIPMetaBundle(ageBits = ageBits)).apply(
      tag = meta.tag,
      valid = true.B,
      dirty = Mux(req.write, (!ro).B, meta.dirty),
      rrpv = 0.U
    )
  )

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil =
    Enum(9)
  val state = RegInit(s_idle)
  val needFlush = RegInit(false.B)

  when(io.flush && (state =/= s_idle)) { needFlush := true.B }
  when(io.out.fire && needFlush) { needFlush := false.B }

  val readBeatCnt = Counter(LineBeats)
  val writeBeatCnt = Counter(LineBeats)

  val s2_ilde :: s2_dataReadWait :: s2_dataOK :: Nil = Enum(3)
  val state2 = RegInit(s2_ilde)

  io.dataReadBus.apply(
    valid =
      (state === s_memWriteReq || state === s_release) && (state2 === s2_ilde),
    setIdx = Cat(
      addr.index,
      Mux(state === s_release, readBeatCnt.value, writeBeatCnt.value)
    )
  )
  val dataWay = RegEnable(io.dataReadBus.resp.data, state2 === s2_dataReadWait)
  val dataHitWay = Mux1H(io.in.bits.waymask, dataWay).data

  switch(state2) {
    is(s2_ilde) { when(io.dataReadBus.req.fire) { state := s2_dataReadWait } }
    is(s2_dataReadWait) { state2 := s2_dataOK }
    is(s2_dataOK) {
      when(io.mem.req.fire || hitReadBurst && io.out.ready) {
        state2 := s2_ilde
      }
    }
  }

  val raddr =
    (if (XLEN == 64) Cat(req.addr(PAddrBits - 1, 3), 0.U(3.W))
     else Cat(req.addr(PAddrBits - 1, 2), 0.U(2.W)))
  // dirty block address
  val waddr = Cat(meta.tag, addr.index, 0.U(OffsetBits.W))
  io.mem.req.bits.apply(
    addr = Mux(state === s_memReadReq, raddr, waddr),
    size = (if (XLEN == 64) "b11".U else "b10".U),
    data = dataHitWay,
    strb = Fill(DataBytes, 1.U),
    write = state =/= s_memReadReq,
    last = writeBeatCnt.value === (LineBeats - 1).U,
    len = LineBeats.U
  )

  /* Mem通道握手信号 */
  io.mem.resp.ready := true.B
  io.mem.req.valid := (state === s_memReadReq) || ((state === s_memWriteReq) && (state2 === s2_dataOK))

  /* MMIO通道驱动与握手信号 */
  io.mmio.req.bits := req
  io.mmio.resp.ready := true.B
  io.mmio.req.valid := (state === s_mmioReq)

  /* FlushSRAM通道驱动与握手信号 */
  val afterFirstRead = RegInit(false.B)
  val alreadyOutFire = RegEnable(true.B, false.B, io.out.fire) // Reset: false.B
  val readingFirst =
    !afterFirstRead && io.mem.resp.fire && (state === s_memReadResp)
  val inRdataRegDemand = RegEnable(
    Mux(mmio, io.mmio.resp.bits.data, io.mem.resp.bits.data),
    Mux(mmio, state === s_mmioResp, readingFirst)
  )

  val releaseLast = Counter(state === s_release, LineBeats)._2

  val respToL1Fire = hitReadBurst && io.out.ready && state2 === s2_dataOK
  val respToL1Last = Counter(
    (state === s_idle || state === s_release && state2 === s2_dataOK) && hitReadBurst && io.out.ready,
    LineBeats
  )._2

  switch(state) {
    is(s_idle) {
      afterFirstRead := false.B
      alreadyOutFire := false.B
      when(hitReadBurst && io.out.ready) {
        state := s_release
        readBeatCnt.value := Mux(
          addr.wordIndex === (LineBeats - 1).U,
          0.U,
          (addr.wordIndex + 1.U)
        )
      }.elsewhen((miss || mmio) && !io.flush) {
        state := Mux(
          mmio,
          s_mmioReq,
          Mux(!ro.B && meta.dirty, s_memWriteReq, s_memReadReq)
        )
      }
      if (Settings.get("TraceCache")) Debug("[State1 s_idle]\n")
    }

    is(s_mmioReq) {
      when(io.mmio.req.fire) {
        state := s_mmioResp;
        if (Settings.get("TraceCache")) Debug("[State1 s_mmioReq]\n")
      }
    }
    is(s_mmioResp) {
      when(io.mmio.resp.fire) {
        state := s_wait_resp;
        if (Settings.get("TraceCache")) Debug("[State1 s_mmioResp]\n")
      }
    }

    is(s_release) {
      when(respToL1Fire) { readBeatCnt.inc() }
      when(releaseLast || respToL1Fire && respToL1Last) { state := s_idle }
      if (Settings.get("TraceCache")) Debug("[State1 s_release]\n")
    }

    is(s_memReadReq) {
      when(io.mem.req.fire) {
        state := s_memReadResp
        readBeatCnt.value := addr.wordIndex
        if (Settings.get("TraceCache")) Debug("[State1 s_memReadReq]\n")
      }
    }

    is(s_memReadResp) {
      when(io.mem.resp.fire) {
        afterFirstRead := true.B
        readBeatCnt.inc()
        when(req.write && req.len =/= 1.U) { writeL2BeatCnt.value := 0.U }
        when(io.mem.resp.bits.last) { state := s_wait_resp }
      }
      if (Settings.get("TraceCache")) Debug("[State1 s_memReadResp]\n")
    }

    is(s_memWriteReq) {
      when(io.mem.req.fire) { writeBeatCnt.inc() }
      when(io.mem.req.bits.last && io.mem.req.fire) { state := s_memWriteResp }
      if (Settings.get("TraceCache")) Debug("[State1 s_memWriteReq]\n")
    }

    is(s_memWriteResp) {
      when(io.mem.resp.fire) { state := s_memReadReq };
      if (Settings.get("TraceCache")) Debug("[State1 s_memWriteResp]\n")
    }
    is(s_wait_resp) {
      when(io.out.fire || needFlush || alreadyOutFire) { state := s_idle };
      if (Settings.get("TraceCache")) Debug("[State1 s_wait_resp]\n")
    }
  }

  // dataRefill
  val dataRefill = MaskData(
    io.mem.resp.bits.data,
    req.data,
    Mux(readingFirst, wordMask, 0.U(DataBits.W))
  )
  val dataRefillWriteBus = Wire(CacheDataArrayWriteBus()).apply(
    valid = (state === s_memReadResp) && io.mem.resp.fire,
    setIdx = Cat(addr.index, readBeatCnt.value),
    data = Wire(new DataBundle).apply(dataRefill),
    waymask = io.in.bits.waymask
  )

  dataWriteArb.io.in(0) <> dataHitWriteBus.req
  dataWriteArb.io.in(1) <> dataRefillWriteBus.req
  io.dataWriteBus.req <> dataWriteArb.io.out // Forwarding

  val metaRefillWriteBus = Wire(CacheMetaArrayWriteBus()).apply(
    valid =
      (state === s_memReadResp) && io.mem.resp.fire && io.mem.resp.bits.last && !io.mem.resp.bits.write,
    data = Wire(new SRRIPMetaBundle(ageBits = ageBits)).apply(
      valid = true.B,
      tag = addr.tag,
      dirty = !ro.B && req.write,
      rrpv = (1.U << ageBits) - 2.U
    ),
    setIdx = getMetaIdx(req.addr),
    waymask = io.in.bits.waymask
  )
  metaWriteArb.io.in(0) <> metaHitWriteBus.req
  metaWriteArb.io.in(1) <> metaRefillWriteBus.req
  io.metaWriteBus.req <> metaWriteArb.io.out // Forwarding and Write to SRAM
  io.forwardRRPVBios := maxRRPV - io.in.bits.crtMaxRRPV

  val metaFlushWriteBus = Wire(CacheMetaArrayFlushBus()).apply(
    valid = io.in.bits.crtMaxRRPV =/= maxRRPV,
    waymask = io.in.bits.flushWaymask,
    data = Wire(new SRRIPMetaBundle(ageBits = ageBits))
      .apply(rrpv = io.in.bits.crtMaxRRPV),
    cmd = FlushCmd.PLUS
  )
  io.flushBus <> metaFlushWriteBus // Forwarding and Write to SRAM
// TODO 为非多发射配置数据BIOS

  if (cacheLevel == 2) {
    when((state === s_memReadResp) && io.mem.resp.fire && req.len =/= 1.U) {
      // readBusrt request miss
      io.out.bits.data := dataRefill
      io.out.bits.last := io.mem.resp.bits.last
      io.out.bits.write := false.B
    }.elsewhen(req.write) {
      // write burst or last request, no matter hit or miss
      io.out.bits.data := Mux(hit, dataRead, inRdataRegDemand)
      io.out.bits.write := true.B
      io.out.bits.last := DontCare
    }.elsewhen(hitReadBurst && state === s_release) {
      // readBurst request hit
      io.out.bits.data := dataHitWay
      io.out.bits.write := false.B
      io.out.bits.last := respToL1Last
    }.otherwise {
      io.out.bits.data := Mux(hit, dataRead, inRdataRegDemand)
      io.out.bits.write := req.write
      io.out.bits.last := req.last
    }
  } else {
    io.out.bits.data := Mux(hit, dataRead, inRdataRegDemand)
    io.out.bits.write := io.in.bits.req.write
    io.out.bits.last := true.B
  }
  io.out.bits.user.zip(req.user).map { case (o, i) => o := i }
  io.out.bits.id.zip(req.id).map { case (o, i) => o := i }

  io.out.valid := io.in.valid && Mux(
    (req.len =/= 1.U) && (cacheLevel == 2).B,
    Mux(
      req.write && (hit || !hit && state === s_wait_resp),
      true.B,
      (state === s_memReadResp && io.mem.resp.fire && !req.write)
    ) || (respToL1Fire && respToL1Last && state === s_release),
    Mux(
      hit,
      true.B,
      Mux(
        req.write || mmio,
        state === s_wait_resp,
        afterFirstRead && !alreadyOutFire
      )
    )
  )

  // 在使用关键字优先技术时，s2和s3之间的流水线寄存器在处理完一个miss req之前不能被重写。使用io.isFinish来指req结束的时候。
  io.isFinish := Mux(
    hit || req.write,
    io.out.fire,
    (state === s_wait_resp) && (io.out.fire || alreadyOutFire)
  )

  io.in.ready := io.out.ready && (state === s_idle && !hitReadBurst) && !miss
  io.dataReadRespToL1 := hitReadBurst && (state === s_idle && io.out.ready || state === s_release && state2 === s2_dataOK)

  assert(!(metaHitWriteBus.req.valid && metaRefillWriteBus.req.valid))
  assert(!(dataHitWriteBus.req.valid && dataRefillWriteBus.req.valid))
  assert(!(!ro.B && io.flush), "only allow to flush icache")
  if (Settings.get("TraceCache")) {
//		Debug(io.metaWriteBus.req.fire, "%d: [" + cacheName + "S3]: metaWrite idx %x wmask %b meta %x%x:%x\n", GTimer(), io.metaWriteBus.req.bits.setIdx, io.metaWriteBus.req.bits.waymask.get, io.metaWriteBus.req.bits.data.valid, io.metaWriteBus.req.bits.data.dirty, io.metaWriteBus.req.bits.data.tag)
    Debug(
      "in.vr (%d,%d) hit = %x state = %d addr = %x isFinish %d\n",
      io.in.valid,
      io.in.ready,
      hit,
      state,
      req.addr,
      io.isFinish
    )
    Debug(
      "out.valid %d rdata %x user %x id %x\n",
      io.out.valid,
      io.out.bits.data,
      io.out.bits.user.getOrElse(0.U),
      io.out.bits.id.getOrElse(0.U)
    )
    Debug(
      "DHW (%d,%d) data %x setIdx %x MHW (%d,%d)\n",
      dataHitWriteBus.req.valid,
      dataHitWriteBus.req.ready,
      dataHitWriteBus.req.bits.data.asUInt,
      dataHitWriteBus.req.bits.setIdx,
      dataHitWriteBus.req.valid,
      metaHitWriteBus.req.ready
    )
//		Debug("DreadCache %x\n", io.in.bits.datas.asUInt)
    Debug(
      "useFD %d isFD %d FD %x DreadArray %x dataRead %x inwaymask %x FDwaymask %x\n",
      useForwardData,
      io.in.bits.isForwardData,
      io.in.bits.forwardData.data.data,
      dataReadArray,
      dataRead,
      io.in.bits.waymask,
      io.in.bits.forwardData.waymask.getOrElse("b1".U)
    )
    Debug(
      io.dataWriteBus.req.fire,
      "[WB] waymask %b data %x setIdx %x\n",
      io.dataWriteBus.req.bits.waymask.get.asUInt,
      io.dataWriteBus.req.bits.data.asUInt,
      io.dataWriteBus.req.bits.setIdx
    )
    Debug(
      (state === s_memWriteReq) && io.mem.req.fire,
      "[COUTW] cnt %x addr %x data %x last %b write %b len %d size %x strb %x tag %x idx %x waymask %b\n",
      writeBeatCnt.value,
      io.mem.req.bits.addr,
      io.mem.req.bits.data,
      io.mem.req.bits.last,
      io.mem.req.bits.write,
      io.mem.req.bits.len,
      io.mem.req.bits.size,
      io.mem.req.bits.strb,
      addr.tag,
      getMetaIdx(req.addr),
      io.in.bits.waymask
    )
    Debug(
      (state === s_memReadReq) && io.mem.req.fire,
      "[COUTR] addr %x tag %x idx %x waymask %b\n",
      io.mem.req.bits.addr,
      addr.tag,
      getMetaIdx(req.addr),
      io.in.bits.waymask
    )
    Debug(
      (state === s_memReadResp) && io.mem.resp.fire,
      "[COUTR] cnt %x data %x tag %x idx %x waymask %b\n",
      readBeatCnt.value,
      io.mem.resp.bits.data,
      addr.tag,
      getMetaIdx(req.addr),
      io.in.bits.waymask
    )
  }
}

class SRRIPCache(implicit val cacheConfig: CacheConfig)
    extends CacheModule
    with HasCacheIO
    with HasSRRIPConst {
  implicit val moduleName: String = cacheName
  val io = IO(new CacheIO)
  val debugBus = IO(Flipped(new DebugBus))

  // CPU pipeline
  val s1 = Module(new SRRIPCacheStage1)
  val s2 = Module(new SRRIPCacheStage2)
  val s3 = Module(new SRRIPCacheStage3)
  // Here, We need add another read port to support debugBus. FIXME when remove debugBus. Dont remove FIXME tag until you need build a FPGA code.
  val metaArray = Module(
    new SRAMTemplateWithArbiter(
      nRead = 1,
      new SRRIPMetaBundle(ageBits = ageBits),
      set = Sets,
      way = Ways,
      shouldReset = true,
      flushSet = true
    )
  )
  val dataArray = Module(
    new SRAMTemplateWithArbiter(
      nRead = 2,
      new DataBundle,
      set = Sets * LineBeats,
      way = Ways,
      shouldReset = false,
      flushSet = false
    )
  )

  /* DebugBus */
  debugBus.req.ready := true.B
  debugBus.resp.bits := DontCare
  debugBus.resp.valid := debugBus.req.valid
  val resp =
    MuxLookup(debugBus.req.bits.cmd, DebugBusParameters.SLAVE_UNSPT_CMD)(
      Seq(
        DebugBusParameters.SCAN_META -> DebugBusParameters.SLAVE_UNSPT_CMD,
        DebugBusParameters.SCAN_DATA -> DebugBusParameters.SLAVE_UNSPT_CMD
      )
    )

  debugBus.resp.bits.len := debugBus.req.bits.len
//	for (i <- 0 until 8) {
//		when (i.U <= debugBus.req.bits.len) {
//			debugBus.resp.bits.datas(i) := MuxLookup(debugBus.req.bits.cmd, 0.U, Seq(
//				DebugBusParameters.SCAN_META	-> metaArray.io.r,
//				DebugBusParameters.SCAN_DATA	-> dataArray()
//				))
//		}.otherwise {
//			debugBus.resp.bits.datas(i) := 0.U // Set 0.U as default val.
//		}
//	}

  if (cacheName == "icache") {
    debugBus.resp.bits.resp := Mux(
      debugBus.req.valid && debugBus.req.bits.id === DebugBusParameters.L1ICache,
      resp,
      DebugBusParameters.SLAVE_NOT_ME
    )
  } else if (cacheName == "dcache") {
    debugBus.resp.bits.resp := Mux(
      debugBus.req.valid && debugBus.req.bits.id === DebugBusParameters.L1DCache,
      resp,
      DebugBusParameters.SLAVE_NOT_ME
    )
  }

  if (cacheName == "icache") {
    // flush icache when executing fence.i
    val flushICache = WireInit(false.B)
    BoringUtils.addSink(flushICache, "MOUFlushICache")
    metaArray.reset := reset.asBool || flushICache
  }

  s1.io.in <> io.in.req

  PipelineConnect(s1.io.out, s2.io.in, s2.io.out.fire, io.flush(0))
  PipelineConnect(s2.io.out, s3.io.in, s3.io.isFinish, io.flush(1))
  io.in.resp <> s3.io.out
  s3.io.flush := io.flush(1)
  io.mem <> s3.io.mem // MEM请求计算完后，到达Stage3给出
  io.mmio <> s3.io.mmio // MMIO请求随动到Stage3
  io.empty := !s2.io.in.valid && !s3.io.in.valid // s2没有有效输入，s3没有有效输入，当然Cache为空

  io.in.resp.valid := s3.io.out.valid || s3.io.dataReadRespToL1

  metaArray.io.r(0) <> s1.io.metaReadBus;
  dataArray.io.r(0) <> s1.io.dataReadBus // Hit, return data directly
  dataArray.io.r(
    1
  ) <> s3.io.dataReadBus // Miss, and refill correct line at stage3
  metaArray.io.w <> s3.io.metaWriteBus; dataArray.io.w <> s3.io.dataWriteBus

  Debug(
    s3.io.metaWriteBus.req.fire || s3.io.dataWriteBus.req.fire,
    "[Cache Write meta%d data%d] setIdx %d way %b v%d(rrpv %x tag %x) d%d(data 0x%x)\n",
    s3.io.metaWriteBus.req.fire,
    s3.io.dataWriteBus.req.fire,
    s3.io.metaWriteBus.req.bits.setIdx,
    s3.io.metaWriteBus.req.bits.waymask.getOrElse("b0".U),
    s3.io.metaWriteBus.req.bits.data.valid,
    s3.io.metaWriteBus.req.bits.data.rrpv,
    s3.io.metaWriteBus.req.bits.data.tag,
    s3.io.metaWriteBus.req.bits.data.dirty,
    s3.io.dataWriteBus.req.bits.data.data
  )
  Debug(
    s1.io.metaReadBus.req.fire && s1.io.dataReadBus.req.fire,
    "[Cache Read S1] {Req} setIdx %d {Resp} v%d(rrpv %x tag %x) d%d(data0 %x data1 %x data2 %x data3 %x)\n",
    s1.io.metaReadBus.req.bits.setIdx,
    s1.io.metaReadBus.resp.data(0).valid,
    s1.io.metaReadBus.resp.data(0).rrpv,
    s1.io.metaReadBus.resp.data(0).tag,
    s1.io.metaReadBus.resp.data(0).dirty,
    s1.io.dataReadBus.resp.data(0).data,
    s1.io.dataReadBus.resp.data(1).data,
    s1.io.dataReadBus.resp.data(2).data,
    s1.io.dataReadBus.resp.data(3).data
  )
  Debug(
    s3.io.dataReadBus.req.fire,
    "[Cache Read S3(Refill)] {Req} setIdx %d {Resp} (data0 %x data1 %x data2 %x data3 %x)\n",
    s3.io.dataReadBus.req.bits.setIdx,
    s3.io.dataReadBus.resp.data(0).data,
    s3.io.dataReadBus.resp.data(1).data,
    s3.io.dataReadBus.resp.data(2).data,
    s3.io.dataReadBus.resp.data(3).data
  )

//	metaArray.io.flush <> s3.io.flushSRAM
  metaArray.io.flush.foreach { flushIO =>
    flushIO <> s3.io.flushBus
  }

  s2.io.metaReadResp := s1.io.metaReadBus.resp.data
  s2.io.dataReadResp := s1.io.dataReadBus.resp.data
  s2.io.metaWriteBus := s3.io.metaWriteBus
  s2.io.dataWriteBus := s3.io.dataWriteBus
  s2.io.forwardRRPVBios := s3.io.forwardRRPVBios

  if (EnableOutOfOrderExec) {
    BoringUtils.addSource(
      s3.io.out.fire && s3.io.in.bits.hit,
      "perfCntCondM" + cacheName + "Hit"
    )
    BoringUtils.addSource(
      s3.io.in.valid && !s3.io.in.bits.hit,
      "perfCntCondM" + cacheName + "Miss"
    )
    BoringUtils.addSource(s1.io.in.fire, "perfCntCondM" + cacheName + "Req")
  }

  if (Settings.get("TraceCache")) {
    Debug(
      "Req @<%x---%x>[S1]<%x---%x>[S2]<%x---%x>[S3]<%x---%x>@ Resp\n",
      s1.io.in.valid,
      s1.io.in.ready,
      s1.io.out.valid,
      s2.io.in.ready,
      s2.io.out.valid,
      s3.io.in.ready,
      s3.io.out.valid,
      s3.io.out.ready
    )
    Debug(
      "[Mread] [S1]<%x---%x>[Array][S2 Read]; [Dread] [S1]<%x---%x>[Array][S2 Read] [S3]<%x---%x>[SRAM][S2 Write]\n",
      s1.io.metaReadBus.req.valid,
      metaArray.io.r(0).req.ready,
      s1.io.dataReadBus.req.valid,
      dataArray.io.r(0).req.ready,
      s3.io.dataReadBus.req.valid,
      dataArray.io.r(1).req.ready
    )
    Debug(
      "[Mwrite] [s3]<%x---%x>[Array] [Dwrite] [s3]<%x---%x>[Array]\n",
      s3.io.metaWriteBus.req.valid,
      metaArray.io.w.req.ready,
      s3.io.dataWriteBus.req.valid,
      dataArray.io.w.req.ready
    )
  }
  when(s1.io.in.valid) { Debug(p"[${cacheName}.S1]: ${s1.io.in.bits}\n") }
  when(s2.io.in.valid) { Debug(p"[${cacheName}.S2]: ${s2.io.in.bits.req}\n") }
  when(s3.io.in.valid) { Debug(p"[${cacheName}.S3]: ${s3.io.in.bits.req}\n") }
}
