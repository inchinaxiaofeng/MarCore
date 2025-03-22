package utils

import chisel3._
import chisel3.util._
import module.cache._

class SRAMBundleA(val set: Int) extends Bundle {
  val setIdx = Output(UInt(log2Up(set).W))

  def apply(setIdx: UInt) = {
    this.setIdx := setIdx
    this
  }
}

class SRAMBundleAW[T <: Data](private val gen: T, set: Int, val way: Int = 1)
    extends SRAMBundleA(set) {
  val data = Output(gen)
  val waymask = if (way > 1) Some(Output(UInt(way.W))) else None

  def apply(data: T, setIdx: UInt, waymask: UInt) = {
    this.setIdx := setIdx
    this.data := data
    this.waymask.map(_ := waymask)
    this
  }
}

class SRAMBundleR[T <: Data](private val gen: T, val way: Int = 1)
    extends Bundle {
  val data = Output(Vec(way, gen))
}

class SRAMReadBus[T <: Data](private val gen: T, val set: Int, val way: Int = 1)
    extends Bundle {
  val req = Decoupled(new SRAMBundleA(set))
  val resp = Flipped(new SRAMBundleR(gen, way))

  def apply(valid: Bool, setIdx: UInt) = {
    this.req.bits.apply(setIdx)
    this.req.valid := valid
    this
  }
}

class SRAMWriteBus[T <: Data](
    private val gen: T,
    val set: Int,
    val way: Int = 1
) extends Bundle {
  val req = Decoupled(new SRAMBundleAW(gen, set, way))

  def apply(valid: Bool, data: T, setIdx: UInt, waymask: UInt) = {
    this.req.bits.apply(data = data, setIdx = setIdx, waymask = waymask)
    this.req.valid := valid
    this
  }
}

object FlushCmd {
  def FIXED = "b0".U // All line will be set to a fixed value
  def PLUS = "b1".U

  def apply() = UInt(1.W)
}

class SRAMBundleFlush[T <: Data](private val gen: T, val way: Int = 1)
    extends Bundle {
  val data = Output(gen)
  val waymask = if (way > 1) Some(Output(UInt(way.W))) else None
  val cmd = Output(FlushCmd())

  def apply(data: T, waymask: UInt, cmd: UInt) = {
    this.data := data
    this.waymask.map(_ := waymask)
    this.cmd := cmd
    this
  }
}

class SRAMFlushBus[T <: Data](private val gen: T, val way: Int = 1)
    extends Bundle {
  val req = Decoupled(new SRAMBundleFlush(gen, way))

  def apply(valid: Bool, data: T, waymask: UInt, cmd: UInt) = {
    this.req.bits.apply(data = data, waymask = waymask, cmd = cmd)
    this.req.valid := valid
    this
  }
}

class SRAMTemplate[T <: Data](
    gen: T,
    set: Int,
    way: Int = 1,
    shouldReset: Boolean = false,
    holdRead: Boolean = false,
    singlePort: Boolean = false,
    flushSet: Boolean = false,
    flushPriority: Boolean = false
) extends Module {
  implicit val moduleName: String = this.name
  val io = IO(new Bundle {
    val r = Flipped(new SRAMReadBus(gen, set, way))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
    val flush =
      if (flushSet) Some(Flipped(new SRAMFlushBus(gen, way))) else None
  })

  val wordType = UInt(gen.getWidth.W)
  val array = SyncReadMem(set, Vec(way, wordType))
  val (resetState, resetSet) = (WireInit(false.B), WireInit(0.U))

  // 用计数器，逐个刷新CacheSet
  if (shouldReset) {
    val _resetState = RegInit(true.B)
    val (_resetSet, resetFinish) = Counter(_resetState, set)
    when(resetFinish) { _resetState := false.B }

    resetState := _resetState
    resetSet := _resetSet
  }

  val flushValid =
    if (flushSet) io.flush.get.req.valid
    else false.B // Maybe Could use DontCare
  val flushCmd = if (flushSet) io.flush.get.req.bits.cmd else 0.U
  val flushData = if (flushSet) io.flush.get.req.bits.data.asUInt else 0.U
  val f_waymask =
    if (flushSet) io.flush.get.req.bits.waymask.getOrElse("b1".U) else 0.U

  val (ren, wen, fen) =
    (io.r.req.valid, io.w.req.valid || resetState, flushValid && !resetState)
  val realRen = (if (singlePort) ren && !wen else ren)

  val setIdx = Mux(resetState, resetSet, io.w.req.bits.setIdx)

  val fdataword = MuxLookup(flushCmd, io.w.req.bits.data.asUInt)(
    Seq(
      FlushCmd.FIXED -> (flushData),
      FlushCmd.PLUS -> (flushData) // 确保使用这个命令时不会发生溢出，以避免出现BUG
    )
  )
  val fdata = VecInit(Seq.fill(way)(fdataword))

  val wdataword =
    Mux(resetState, 0.U.asTypeOf(wordType), io.w.req.bits.data.asUInt)
  val w_waymask =
    Mux(resetState, Fill(way, "b1".U), io.w.req.bits.waymask.getOrElse("b1".U))
  val wdata = VecInit(Seq.fill(way)(wdataword))

//	val combData = VecInit(Seq.tabulate(way) {
//		i => Mux(w_waymask(i) && f_waymask(i),
//			Mux(flushPriority, fdata(i), wdata(i)),
//			Mux(w_waymask(i), wdata(i), fdata(i)))
//	})
  val combData = VecInit(Seq.tabulate(way) { i =>
    Mux(
      !f_waymask(i) || (w_waymask(i) && !flushPriority.asBool),
      wdata(i),
      fdata(i)
    )
  }) // K-map
  val combWaymask = f_waymask | w_waymask
  when(wen || fen) { array.write(setIdx, combData, combWaymask.asBools) }

  val rdata = (if (holdRead) ReadAndHold(array, io.r.req.bits.setIdx, realRen)
               else
                 array.read(io.r.req.bits.setIdx, realRen)).map(_.asTypeOf(gen))
  io.r.resp.data := VecInit(rdata)
  io.r.req.ready := !resetState && (if (singlePort) !wen else true.B)
  io.w.req.ready := true.B
  io.flush.foreach { flushIO =>
    flushIO.req.ready := true.B
  }
}

class SRAMTemplateWithArbiter[T <: Data](
    nRead: Int,
    gen: T,
    set: Int,
    way: Int = 1,
    shouldReset: Boolean = false,
    flushSet: Boolean = false,
    flushPriority: Boolean = false
) extends Module {
  implicit val moduleName: String = this.name
  val io = IO(new Bundle {
    val r = Flipped(Vec(nRead, new SRAMReadBus(gen, set, way)))
    val w = Flipped(new SRAMWriteBus(gen, set, way))
    val flush =
      if (flushSet) Some(Flipped(new SRAMFlushBus(gen, way))) else None
  })

  val ram = Module(
    new SRAMTemplate(
      gen,
      set,
      way,
      shouldReset,
      holdRead = false,
      singlePort = true,
      flushSet = flushSet,
      flushPriority = false
    )
  )
  ram.io.w <> io.w

  val readArb = Module(new Arbiter(chiselTypeOf(io.r(0).req.bits), nRead))
  readArb.io.in <> io.r.map(_.req)
  ram.io.r.req <> readArb.io.out

  io.flush.foreach { flushIO =>
    flushIO <> ram.io.flush.get
  }

  // latch read results
  io.r.map {
    case r => {
      r.resp.data := HoldUnless(ram.io.r.resp.data, RegNext(r.req.fire))
    }
  }
}
