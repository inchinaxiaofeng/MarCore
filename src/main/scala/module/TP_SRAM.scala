package module

import chisel3._
import chisel3.util._

import bus.axi4._
import defs._
import utils._

// Can't Change In YSYX
class MEM extends BlackBox {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val iRen = Input(Bool())
    val iWen = Input(Bool())
    val iReadAddr = Input(UInt(32.W))
    val iWriteAddr = Input(UInt(32.W))
    val iByteMask = Input(UInt(8.W))
    val iWriteData = Input(UInt(64.W))
    val oReadData = Output(UInt(64.W))
  })
}

class TP_SRAM(cnt: Int) extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(Flipped(new AXI4))
  assert(
    !io.ar.valid || (io.ar.bits.burst === AXI4Parameters.BURST_WRAP && (io.ar.bits.len === 1.U | io.ar.bits.len === 3.U | io.ar.bits.len === 7.U | io.ar.bits.len === 15.U))
  )
  assert(
    !io.aw.valid || (io.aw.bits.burst === AXI4Parameters.BURST_WRAP && (io.aw.bits.len === 1.U | io.aw.bits.len === 3.U | io.aw.bits.len === 7.U | io.aw.bits.len === 15.U))
  )

  val mem = Module(new MEM())
  mem.io.clk := clock
  mem.io.rst := reset

  val s_idle :: s_exec :: Nil = Enum(2)
  val readBeatCnt = Counter(cnt)
  val writeBeatCnt = Counter(cnt)
  val state_load = RegInit(s_idle)
  val state_store = RegInit(s_idle)

  switch(state_load) {
    is(s_idle) {
      readBeatCnt.reset()
      when(io.ar.fire) {
        state_load := s_exec
      }
    }

    is(s_exec) {
      readBeatCnt.inc()
      when(io.r.fire && (readBeatCnt.value === io.ar.bits.len)) {
        state_load := s_idle
      }
    }
  }

  switch(state_store) {
    is(s_idle) {
      writeBeatCnt.reset()
      when(io.aw.fire && io.w.fire) {
        writeBeatCnt.inc()
        state_store := s_exec
      }.elsewhen(io.aw.fire) {
        state_store := s_exec
      }
    }

    is(s_exec) {
      when(io.b.fire && (writeBeatCnt.value === io.aw.bits.len)) {
        state_store := s_idle
      }
      when(io.w.fire) { writeBeatCnt.inc() }
    }
  }

//	Debug("R: Addr%x Reg%x W: Addr%x Reg%x\n", io.ar.bits.addr, RegEnable(io.ar.bits.addr, 0.U, io.ar.fire), io.aw.bits.addr, RegEnable(io.aw.bits.addr, 0.U, io.aw.fire))
  val rAddr = Mux(
    io.ar.fire && state_load === s_idle,
    io.ar.bits.addr,
    RegEnable(io.ar.bits.addr, 0.U, io.ar.fire)
  )
  val rBurstType = Mux(
    io.ar.fire && state_load === s_idle,
    io.ar.bits.burst,
    RegEnable(io.ar.bits.burst, 0.U, io.ar.fire)
  )
  val rBeatSize = Mux(
    io.ar.fire && state_load === s_idle,
    io.ar.bits.size,
    RegEnable(io.ar.bits.size, 0.U, io.ar.fire)
  )
  val rBurstLen = Mux(
    io.ar.fire && state_load === s_idle,
    io.ar.bits.len + 1.U,
    RegEnable(io.ar.bits.len + 1.U, 0.U, io.ar.fire)
  )

  val wAddr = Mux(
    io.aw.fire && state_store === s_idle,
    io.aw.bits.addr,
    RegEnable(io.aw.bits.addr, 0.U, io.aw.fire)
  )
  val wBurstType = Mux(
    io.aw.fire && state_store === s_idle,
    io.aw.bits.burst,
    RegEnable(io.aw.bits.burst, 0.U, io.aw.fire)
  )
  val wBeatSize = Mux(
    io.aw.fire && state_store === s_idle,
    io.aw.bits.size,
    RegEnable(io.aw.bits.size, 0.U, io.aw.fire)
  )
  val wBurstLen = Mux(
    io.aw.fire && state_store === s_idle,
    io.aw.bits.len + 1.U,
    RegEnable(io.aw.bits.len + 1.U, 0.U, io.aw.fire)
  )
  // 步进地址
  val rStepAddr = 1.U << rBeatSize
  val wStepAddr = 1.U << wBeatSize

  // Incr addr
  val rIncAddr = rAddr + (readBeatCnt.value * rStepAddr)
  val wIncAddr = wAddr + (writeBeatCnt.value * wStepAddr)

  // Wrap addr
  val rAlignedAddr = (rAddr / rStepAddr) * rBurstLen
  val rLowerBoundAddr =
    (rAddr / (rStepAddr * rBurstLen)) * (rStepAddr * rBurstLen)
  val rUpperBoundAddr = rLowerBoundAddr + (rStepAddr * io.ar.bits.len)
  val wAlignedAddr = (wAddr / wStepAddr) * wBurstLen
  val wLowerBoundAddr =
    (wAddr / (wStepAddr * wBurstLen)) * (wStepAddr * wBurstLen)
  val wUpperBoundAddr = wLowerBoundAddr + (wStepAddr * io.aw.bits.len)

  val rWrapAddr = RegInit(0.U(AXI4Parameters.addrBits.W))
  rWrapAddr := Mux(
    io.ar.valid,
    io.ar.bits.addr,
    Mux(rWrapAddr === rUpperBoundAddr, rLowerBoundAddr, rWrapAddr + rStepAddr)
  )
  val wWrapAddr = RegInit(0.U(AXI4Parameters.addrBits.W))
  wWrapAddr := Mux(
    io.aw.valid,
    io.aw.bits.addr,
    Mux(wWrapAddr === wUpperBoundAddr, wLowerBoundAddr, wWrapAddr + wStepAddr)
  )
  Debug("WARP rWrapAddr%x wWrapAddr%x\n", rWrapAddr, wWrapAddr)
  Debug(
    "io.aw.valid%d-(io.aw.bits.addr%x,wWrapAddr===wUpperBoundAddr%d-(wLowerBoundAddr%x, wWrapAddr+wStepAddr%x))\n",
    io.aw.valid,
    io.aw.bits.addr,
    wWrapAddr === wUpperBoundAddr,
    wLowerBoundAddr,
    wWrapAddr + wStepAddr
  )

  val iWriteData = RegEnable(io.w.bits.data, 0.U, io.w.valid)
  val iByteMask = RegEnable(io.w.bits.strb, 0.U, io.w.valid)

  /* Just push the data to SRAM and use enable signal control */
  mem.io.iReadAddr := MuxLookup(rBurstType, rIncAddr)(
    Seq(
      AXI4Parameters.BURST_INCR -> rIncAddr,
      AXI4Parameters.BURST_WRAP -> rWrapAddr,
      AXI4Parameters.BURST_FIXED -> rAddr
    )
  )
  mem.io.iWriteAddr := MuxLookup(wBurstType, wIncAddr)(
    Seq(
      AXI4Parameters.BURST_INCR -> wIncAddr,
      AXI4Parameters.BURST_WRAP -> wWrapAddr,
      AXI4Parameters.BURST_FIXED -> wAddr
    )
  )
  mem.io.iWriteData := iWriteData
  mem.io.iByteMask := iByteMask

  Debug("RAddr%x WAddr%x\n", rAddr, wAddr)

  /* Write */
  // Immediately ready
  io.w.ready := state_store === s_idle
  io.aw.ready := state_store === s_idle
  mem.io.iWen := state_store === s_exec
  io.b.valid := state_store === s_exec
  // if not ready, anything can be resp
  io.b.bits.apply(
    resp =
      Mux(io.b.ready, AXI4Parameters.RESP_OKAY, AXI4Parameters.RESP_SLVERR),
    user = 0.U,
    id = 0.U
  )

  /* Read */
  // Immediately ready
  io.ar.ready := state_load === s_idle
  mem.io.iRen := state_load === s_exec
  io.r.valid := state_load === s_exec
  // if not ready, anything can be resp
  io.r.bits.apply(
    data = mem.io.oReadData,
    resp =
      Mux(io.r.ready, AXI4Parameters.RESP_OKAY, AXI4Parameters.RESP_SLVERR),
    user = 0.U,
    id = 0.U,
    last = readBeatCnt.value === io.ar.bits.len
  )
  Debug(
    "State_Load(Read)%d State_Store(Write)%d Fire: AR%d AW%d W%d; R MUX %d W MUX %d\n",
    state_load,
    state_store,
    io.ar.fire,
    io.aw.fire,
    io.w.fire,
    io.ar.fire && state_load === s_idle,
    io.aw.fire && state_store === s_idle
  )
}
