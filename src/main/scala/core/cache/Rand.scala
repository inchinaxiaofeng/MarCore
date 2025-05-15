package core.cache

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.cacheBus._

sealed trait HasRANDConst extends HasCacheConst {
  def CacheMetaArrayReadBus() =
    new SRAMReadBus(new MetaBundle(), set = Sets, way = Ways)
  def CacheDataArrayReadBus() =
    new SRAMReadBus(new DataBundle(), set = Sets * LineBeats, way = Ways)
  def CacheMetaArrayWriteBus() =
    new SRAMWriteBus(new MetaBundle(), set = Sets, way = Ways)
  def CacheDataArrayWriteBus() =
    new SRAMWriteBus(new DataBundle(), set = Sets * LineBeats, way = Ways)
}

sealed class RANDStage1IO(implicit val cacheConfig: CacheConfig)
    extends CacheBundle {
  val req = new CacheBusReqBundle(userBits = userBits, idBits = idBits)
}

sealed class RANDCacheStage1(implicit val cacheConfig: CacheConfig)
    extends CacheStageModule
    with HasRANDConst {
  implicit val moduleName: String = cacheName
  class CacheStage1IO extends Bundle {
    val in = Flipped(
      Decoupled(new CacheBusReqBundle(userBits = userBits, idBits = idBits))
    )
    val out = Decoupled(new RANDStage1IO)
    val metaReadBus = CacheMetaArrayReadBus()
    val dataReadBus = CacheDataArrayReadBus()
  }
  val io = IO(new CacheStage1IO)

  if (ro) when(io.in.fire) { assert(!io.in.bits.write) }
  Debug(
    io.in.fire,
    "[L1$] Cache stage 1, addr in %x user %x id %x\n",
    io.in.bits.addr,
    io.in.bits.user.getOrElse(0.U),
    io.in.bits.id.getOrElse(0.U)
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

sealed class RANDStage2IO(implicit val cacheConfig: CacheConfig)
    extends CacheBundle
    with HasRANDConst {
  val req = new CacheBusReqBundle(userBits = userBits, idBits = idBits)
  val metas = Vec(Ways, new MetaBundle)
  val datas = Vec(Ways, new DataBundle)
  val hit = Output(Bool())
  val waymask = Output(UInt(Ways.W))
  val mmio = Output(Bool())
  val isForwardData = Output(Bool())
  val forwardData = Output(CacheDataArrayWriteBus().req.bits)
}

sealed class RANDCacheStage2(implicit val cacheConfig: CacheConfig)
    extends CacheStageModule
    with HasRANDConst {
  implicit val moduleName: String = cacheName
  class CacheStage2IO extends Bundle {
    val in = Flipped(Decoupled(new RANDStage1IO))
    val out = Decoupled(new RANDStage2IO)
    val metaReadResp = Flipped(Vec(Ways, new MetaBundle))
    val dataReadResp = Flipped(Vec(Ways, new DataBundle))
    val metaWriteBus = Input(CacheMetaArrayWriteBus()) // Refill
    val dataWriteBus = Input(CacheDataArrayWriteBus())
  }
  val io = IO(new CacheStage2IO)

  val req = io.in.bits.req
  val addr = req.addr.asTypeOf(addrBundle)
  // Write back meta from stage 3
  // 当前使用的Meta在当前Cycle会被前一个访存请求所更新
  val isForwardMeta =
    io.in.valid && io.metaWriteBus.req.valid && io.metaWriteBus.req.bits.setIdx === getMetaIdx(
      req.addr
    )
  val isForwardMetaReg = RegInit(false.B)
  when(isForwardMeta) { isForwardMetaReg := true.B }
  // 当指令已经装入前端的寄存器中，则下一拍翻转；如果前级根本没有请求，那么值时可以正确读取的，也不需要写Reg
  when(io.in.fire || !io.in.valid) { isForwardMetaReg := false.B }
  val forwardMetaReg = RegEnable(io.metaWriteBus.req.bits, isForwardMeta)

  val metaWay = Wire(
    Vec(Ways, chiselTypeOf(forwardMetaReg.data))
  ) // Meta Data with way
  val pickForwardMeta = isForwardMetaReg || isForwardMeta // Use forward data
  val forwardMeta = Mux(
    isForwardMeta,
    io.metaWriteBus.req.bits,
    forwardMetaReg
  ) // 不是当前的Forwarding，认为是旧的forwarding
  val forwardWaymask = forwardMeta.waymask.getOrElse("1".U).asBools
  // NOTE: 这里是用来检测从CacheArray中返回的值的
  Debug(
    io.in.valid,
    "[Read Resp(S2)] pickForward%d Forward v%d tag%x d%d data\n",
    pickForwardMeta,
    forwardMeta.data.valid,
    forwardMeta.data.tag,
    forwardMeta.data.dirty
  )
  forwardWaymask.zipWithIndex.map { case (w, i) =>
    metaWay(i) := Mux(
      pickForwardMeta && w,
      forwardMeta.data,
      io.metaReadResp(i)
    )
    Debug(
      io.in.valid,
      "[Read Resp(S2)] ReadResp%d v%d tag%x d%d data%x\n",
      i.U,
      io.metaReadResp(i).valid,
      io.metaReadResp(i).tag,
      io.metaReadResp(i).dirty,
      io.dataReadResp(i).data
    )
  }

  val hitVec = VecInit(
    metaWay.map(m => m.valid && (m.tag === addr.tag) && io.in.valid)
  ).asUInt // valid且tag相同为命中
//	Debug("Req Addr tag%x Vec1 v%d tag%x Vec1 v%d tag%x Vec2 v%d tag%x Vec3 v%d tag%x\n", addr.tag, metaWay(0).valid, metaWay(0).tag, metaWay(1).valid, metaWay(1).tag, metaWay(2).valid, metaWay(2).tag, metaWay(3).valid, metaWay(3).tag)
  val hit = io.in.valid && hitVec.orR

  val victimWaymask =
    if (Ways > 1) (1.U << LFSR64()(log2Up(Ways) - 1, 0)) else "b1".U

  val invalidVec = VecInit(metaWay.map(m => !m.valid)).asUInt
  val hasInvalidWay = invalidVec.orR
  val refillInvalidWaymask = UIntToOH(PriorityEncoder(invalidVec), Ways)

  val waymask = Mux(
    io.out.bits.hit,
    hitVec,
    Mux(hasInvalidWay, refillInvalidWaymask, victimWaymask)
  )
  Debug(
    !io.out.bits.hit && !hasInvalidWay,
    "[Vectim] waymask %b\n",
    waymask.asUInt
  )
  assert(waymask.orR)
//	Debug("Waymask %b {hit(%d)Vec %b refillInvalidWaymask %b VictimVec %b}\n", waymask.asUInt, hit, hitVec, refillInvalidWaymask, victimWaymask.asUInt)
  assert(!(io.in.valid && PopCount(waymask) > 1.U))

  io.out.bits.metas := metaWay
  io.out.bits.hit := hit
  io.out.bits.waymask := waymask
  io.out.bits.datas := io.dataReadResp
  io.out.bits.mmio := AddressSpace.isMMIO(req.addr)

  val isForwardData = io.in.valid && (io.dataWriteBus.req match {
    case r =>
      r.valid && r.bits.setIdx === getDataIdx(req.addr)
  })
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
}

sealed class RANDCacheStage3(implicit val cacheConfig: CacheConfig)
    extends CacheStageModule
    with HasRANDConst {
  implicit val moduleName: String = cacheName
  class CacheStage3IO extends Bundle {
    val in = Flipped(Decoupled(new RANDStage2IO))
    val out = Decoupled(
      new CacheBusRespBundle(userBits = userBits, idBits = idBits)
    )
    val isFinish = Output(Bool())
    val flush = Input(Bool())
    val dataReadBus = CacheDataArrayReadBus()
    val dataWriteBus = CacheDataArrayWriteBus()
    val metaWriteBus = CacheMetaArrayWriteBus()

    val mem = new CacheBus()
    val mmio = new CacheBus()

    // TODO use to distinguish prefetch re
    val dataReadRespToL1 = Output(Bool())
  }

  val io = IO(new CacheStage3IO)

  val metaWriteArb = Module(new Arbiter(CacheMetaArrayWriteBus().req.bits, 2))
  val dataWriteArb = Module(new Arbiter(CacheDataArrayWriteBus().req.bits, 2))

  val req = io.in.bits.req
  val addr = req.addr.asTypeOf(addrBundle)
  val mmio = io.in.valid && io.in.bits.mmio
  val hit = io.in.valid && io.in.bits.hit
  val miss = io.in.valid && !io.in.bits.hit
  val hitReadBurst = hit && !req.write && req.len =/= 1.U
  val meta = Mux1H(io.in.bits.waymask, io.in.bits.metas)
  assert(!(mmio && hit), "MMIO request should not hit in cache")

//	val statistic_cache = Module(new STATISTIC_CACHE())
//	statistic_cache.io.clk	:= clock
//	statistic_cache.io.rst	:= reset
//	statistic_cache.io.stat	:= Cat(hit, miss)
//	if (cacheName == "icache")
//		statistic_cache.io.id := 1.U
//	else if (cacheName == "dcache")
//		statistic_cache.io.id := 2.U
//	else
//		statistic_cache.io.id := 0.U

  val useForwardData =
    io.in.bits.isForwardData && io.in.bits.waymask === io.in.bits.forwardData.waymask
      .getOrElse("b1".U)
  val dataReadArray = Mux1H(io.in.bits.waymask, io.in.bits.datas).data
  val dataRead =
    Mux(useForwardData, io.in.bits.forwardData.data.data, dataReadArray)
  val wordMask = Mux(!ro.B && req.write, MaskExpend(req.strb), 0.U(DataBits.W))

  val writeL2BeatCnt = Counter(LineBeats)
  // Only for burst trans, BeatCnt should increase
  when(io.out.fire && req.write && req.len =/= 1.U) { // Burst
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
    data = Wire(new MetaBundle)
      .apply(tag = meta.tag, valid = true.B, dirty = (!ro).B)
  )

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteReq :: s_memWriteResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: s_release :: Nil =
    Enum(9)
  val state = RegInit(s_idle)
  val needFlush = RegInit(false.B)

  when(io.flush && (state =/= s_idle)) { needFlush := true.B }
  when(io.out.fire && needFlush) { needFlush := false.B }

  val readBeatCnt = Counter(LineBeats)
  val writeBeatCnt = Counter(LineBeats)

  Debug(
    "ReadBeatCnt %d WriteBeatCnt %d\n",
    readBeatCnt.value,
    writeBeatCnt.value
  )

  val s2_idle :: s2_dataReadWait :: s2_dataOK :: Nil = Enum(3)
  val state2 = RegInit(s2_idle)

  io.dataReadBus.apply(
    valid =
      (state === s_memWriteReq || state === s_release) && (state2 === s2_idle),
    setIdx = Cat(
      addr.index,
      Mux(state === s_release, readBeatCnt.value, writeBeatCnt.value)
    )
  )
  val dataWay = RegEnable(io.dataReadBus.resp.data, state2 === s2_dataReadWait)
  val dataHitWay = Mux1H(io.in.bits.waymask, dataWay).data

  switch(state2) {
    is(s2_idle) { when(io.dataReadBus.req.fire) { state2 := s2_dataReadWait } }
    is(s2_dataReadWait) { state2 := s2_dataOK }
    is(s2_dataOK) {
      when(io.mem.req.fire || hitReadBurst && io.out.ready) {
        state2 := s2_idle
      }
    }
  }

  // critical word first read
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
    write =
      state =/= s_memReadReq, // Write is default, cuz Write transfer request w chanel.
    last = writeBeatCnt.value === (LineBeats - 1).U,
    len = (LineBeats - 1).U
  )
  Debug(
    "STATE %d STATE2 %d last %d\n",
    state,
    state2,
    writeBeatCnt.value === (LineBeats - 1).U
  )

  // Mem Bundle HandShake signal
  io.mem.resp.ready := true.B
  io.mem.req.valid := (state === s_memReadReq) || ((state === s_memWriteReq) && (state2 === s2_dataOK))
  Debug(io.mem.req.valid, "TRANS\n")

  // MMIO Bundle Handshake signal
  io.mmio.req.bits := req
  io.mmio.resp.ready := true.B
  io.mmio.req.valid := (state === s_mmioReq)

  // FlushSRAM bundle handshake signal
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
        Debug(meta.dirty, "DIRTY!\n")
        state := Mux(
          mmio,
          s_mmioReq,
          Mux(!ro.B && meta.dirty, s_memWriteReq, s_memReadReq)
        )
      }
    }

    is(s_mmioReq) { when(io.mmio.req.fire) { state := s_mmioResp } }
    is(s_mmioResp) { when(io.mmio.resp.fire) { state := s_wait_resp } }

    is(s_release) {
      when(respToL1Fire) { readBeatCnt.inc() }
      when(releaseLast || respToL1Fire && respToL1Last) { state := s_idle }
    }

    is(s_memReadReq) {
      when(io.mem.req.fire) {
        state := s_memReadResp
        readBeatCnt.value := addr.wordIndex
      }
    }

    is(s_memReadResp) {
      when(io.mem.resp.fire) {
        afterFirstRead := true.B
        readBeatCnt.inc()
        when(req.write && req.len =/= 1.U) { writeL2BeatCnt.value := 0.U }
        when(io.mem.resp.bits.last) { state := s_wait_resp }
      }
    }

    is(s_memWriteReq) {
      when(io.mem.req.fire) {
        writeBeatCnt.inc()
        Debug("WriteBeatCnt %d\n", writeBeatCnt.value)
      }
      when(io.mem.req.bits.last && io.mem.req.fire) {
        state := s_memWriteResp
        Debug("To S_memWriteResp\n")
      }
    }

    is(s_memWriteResp) { when(io.mem.resp.fire) { state := s_memReadReq } }
    is(s_wait_resp) {
      when(io.out.fire || needFlush || alreadyOutFire) { state := s_idle }
    }
  }
  Debug("REQ fire %d, last %d\n", io.mem.req.fire, io.mem.req.bits.last)

  // dataRefill (cyan)
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
    setIdx = getMetaIdx(req.addr),
    data = Wire(new MetaBundle)
      .apply(valid = true.B, tag = addr.tag, dirty = !ro.B && req.write),
    waymask = io.in.bits.waymask
  )
  metaWriteArb.io.in(0) <> metaHitWriteBus.req
  metaWriteArb.io.in(1) <> metaRefillWriteBus.req
  io.metaWriteBus.req <> metaWriteArb.io.out // Forwarding and Write to SRAM

  if (cacheLevel == 2) {
    when((state === s_memReadResp) && io.mem.resp.fire && req.len =/= 1.U) {
      // readBurst request miss
      io.out.bits.data := dataRefill
      io.out.bits.write := false.B
      io.out.bits.last := io.mem.resp.bits.last
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

  // 在使用关键字优先技术时，s2和s3之间的流水线寄存器在处理完一个miss req之前不能重写
  // 使用io.isFinish来指导req要结束的时候
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
}

class RANDCache(implicit val cacheConfig: CacheConfig)
    extends CacheModule
    with HasRANDConst {
  implicit val moduleName: String = cacheName
  val s1 = Module(new RANDCacheStage1)
  val s2 = Module(new RANDCacheStage2)
  val s3 = Module(new RANDCacheStage3)
  val metaArray = Module(
    new SRAMTemplateWithArbiter(
      nRead = 1,
      new MetaBundle,
      set = Sets,
      way = Ways,
      shouldReset = true,
      flushSet = false
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

  s1.io.in <> io.in.req
  PipelineConnect(s1.io.out, s2.io.in, s2.io.out.fire, io.flush(0))
  PipelineConnect(s2.io.out, s3.io.in, s3.io.isFinish, io.flush(1))
  io.in.resp <> s3.io.out
  s3.io.flush := io.flush(1)
  io.mem <> s3.io.mem
  io.mmio <> s3.io.mmio
  io.empty := !s2.io.in.valid && !s3.io.in.valid
  // TODO Prefetch
  io.in.resp.valid := s3.io.out.valid || s3.io.dataReadRespToL1

  metaArray.io.r(0) <> s1.io.metaReadBus
  dataArray.io.r(0) <> s1.io.dataReadBus
  dataArray.io.r(1) <> s3.io.dataReadBus
  metaArray.io.w <> s3.io.metaWriteBus
  dataArray.io.w <> s3.io.dataWriteBus

//	Debug(s1.io.metaReadBus.req.valid, "[READ req] meta setIdx %d data setIdx %d\n", s1.io.metaReadBus.req.bits.setIdx, s1.io.dataReadBus.req.bits.setIdx)
//	Debug(s3.io.metaWriteBus.req.valid, "[META Write] setIdx %d tag %x valid %d dirty %d waymask %b\n", s3.io.metaWriteBus.req.bits.setIdx, s3.io.metaWriteBus.req.bits.data.tag, s3.io.metaWriteBus.req.bits.data.valid, s3.io.metaWriteBus.req.bits.data.dirty, s3.io.metaWriteBus.req.bits.waymask.getOrElse(0.U))
//	Debug(s3.io.dataWriteBus.req.valid, "[DATA Write] setIdx %d data %x waymask %b\n", s3.io.dataWriteBus.req.bits.setIdx, s3.io.dataWriteBus.req.bits.data.data, s3.io.dataWriteBus.req.bits.waymask.getOrElse(0.U))
//	Debug(s3.io.mem.req.fire, "[MEM REQ] addr%x len%d size%d data%x strb%b last%d write%d\n", s3.io.mem.req.bits.addr, s3.io.mem.req.bits.len, s3.io.mem.req.bits.size, s3.io.mem.req.bits.data, s3.io.mem.req.bits.strb, s3.io.mem.req.bits.last, s3.io.mem.req.bits.write)
//	Debug(s3.io.mem.resp.fire, "[MEM RESP] data%x last%d write%d\n", s3.io.mem.resp.bits.data, s3.io.mem.resp.bits.last, s3.io.mem.resp.bits.write)

  s2.io.metaReadResp := s1.io.metaReadBus.resp.data
  s2.io.dataReadResp := s1.io.dataReadBus.resp.data
  s2.io.metaWriteBus := s3.io.metaWriteBus
  s2.io.dataWriteBus := s3.io.dataWriteBus

  if (EnableOutOfOrderExec) {}
//	Debug("{IN: s1:(%d,%d) s2:(%d,%d) s3:(%d,%d)} {OUT: s1:(%d,%d) s2:(%d,%d) s3:(%d,%d)}\n", s1.io.in.valid, s1.io.in.ready, s2.io.in.valid, s2.io.in.ready, s3.io.in.valid, s3.io.in.ready, s1.io.out.valid, s1.io.out.ready, s2.io.out.valid, s2.io.out.ready, s3.io.out.valid, s3.io.out.ready)
//	when (s1.io.in.valid) { Debug(p"[${cacheName}.S1]: ${s1.io.in.bits}\n") }
//	when (s2.io.in.valid) { Debug(p"[${cacheName}.S2]: ${s2.io.in.bits.req}\n") }
//	when (s3.io.in.valid) { Debug(p"[${cacheName}.S3]: ${s3.io.in.bits.req}\n") }
}
