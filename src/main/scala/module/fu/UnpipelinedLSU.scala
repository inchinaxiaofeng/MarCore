package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import bus.cacheBus._
import module.cache._
import top.Settings

class UnpipeLSUIO extends FuCtrlIO {
  val wdata = Input(UInt(XLEN.W))
  val instr = Input(UInt(32.W)) // Atom insts need aq rl funct3 bit from instr
  val dmem = new CacheBus(addrBits = VAddrBits)
  val ioLoadAddrMisaligned = Output(Bool()) // TODO: refactor it for new backend
  val ioStoreAddrMisaligned = Output(
    Bool()
  ) // TODO: refactor it for new backend
}

/** 公版LSU的模块
  *
  * 通過操控碼的設計, 將實現與架構分離
  */
class UnpipelinedLSU extends MarCoreModule with HasLSUConst {
  implicit val moduleName: String = this.name
  val io = IO(new UnpipeLSUIO)
  val (valid, srcA, srcB, ctrl) =
    (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
  def access(
      valid: Bool,
      srcA: UInt,
      srcB: UInt,
      ctrl: UInt /*, dtlbPF: Bool*/
  ): UInt = {
    this.valid := valid
    this.srcA := srcA
    this.srcB := srcB
    this.ctrl := ctrl
    io.out.bits
  }
  val lsExecUnit = Module(new LSExecUnit)
  lsExecUnit.io.instr := DontCare

  // 暂时不需要任何关于原子操作的代码
  val storeReq = valid & LSUCtrl.isStore(ctrl)
  val loadReq = valid & LSUCtrl.isLoad(ctrl)

  val funct3 = io.instr(14, 12)

  // LSU control FSM state
  val s_idle :: s_exec :: s_load :: Nil = Enum(3)

  // LSU control FSM
  val state = RegInit(s_idle)

  val addr = if (IndependentAddrCalcState) {
    RegNext(srcA + srcB, state === s_idle)
  } else {
    DontCare
  }

  lsExecUnit.io.in.valid := false.B
  lsExecUnit.io.out.ready := DontCare
  lsExecUnit.io.in.bits.srcA := DontCare
  lsExecUnit.io.in.bits.srcB := DontCare
  lsExecUnit.io.in.bits.ctrl := DontCare
  lsExecUnit.io.wdata := DontCare
  io.out.valid := false.B
  io.in.ready := false.B

  switch(state) {
    is(s_idle) { // calculate address
      lsExecUnit.io.in.valid := false.B
      lsExecUnit.io.out.ready := DontCare
      lsExecUnit.io.in.bits.srcA := DontCare
      lsExecUnit.io.in.bits.srcB := DontCare
      lsExecUnit.io.in.bits.ctrl := DontCare
      lsExecUnit.io.wdata := DontCare
      io.in.ready := false.B // || scInvalid
      io.out.valid := false.B // || scInvalid
      when(valid) { state := s_exec }
      if (!IndependentAddrCalcState) {
        lsExecUnit.io.in.valid := io.in.valid // && !atomReq
        lsExecUnit.io.out.ready := io.out.ready
        lsExecUnit.io.in.bits.srcA := srcA + srcB
        lsExecUnit.io.in.bits.srcB := DontCare
        lsExecUnit.io.in.bits.ctrl := ctrl
        lsExecUnit.io.wdata := io.wdata
        io.in.ready := lsExecUnit.io.out.fire // || scInvalid
        io.out.valid := lsExecUnit.io.out.valid // || scInvalid
        state := s_idle
      }
    }

    is(s_exec) {
      lsExecUnit.io.in.valid := true.B
      lsExecUnit.io.out.ready := io.out.ready
      lsExecUnit.io.in.bits.srcA := addr
      lsExecUnit.io.in.bits.srcB := DontCare
      lsExecUnit.io.in.bits.ctrl := ctrl
      lsExecUnit.io.wdata := io.wdata
      io.in.ready := lsExecUnit.io.out.fire
      io.out.valid := lsExecUnit.io.out.valid
      when(io.out.fire) { state := s_idle }
    }

    is(s_load) {
      lsExecUnit.io.in.valid := true.B
      lsExecUnit.io.out.ready := io.out.ready
      lsExecUnit.io.in.bits.srcA := srcA
      lsExecUnit.io.in.bits.srcB := srcB
      lsExecUnit.io.in.bits.ctrl := ctrl
      lsExecUnit.io.wdata := DontCare
      io.in.ready := lsExecUnit.io.out.fire
      io.out.valid := lsExecUnit.io.out.valid
      when(lsExecUnit.io.out.fire) { state := s_idle } // load finished
    }
  }
  when(io.ioLoadAddrMisaligned || io.ioStoreAddrMisaligned) {
    state := s_idle
    io.out.valid := true.B
    io.in.ready := true.B
  }

  if (Settings.get("TraceLSU"))
    Debug(
      io.out.fire,
      "[LSU-AGU] state %x inv %x inr %x\n",
      state,
      io.in.valid,
      io.in.ready
    )

  // Controled by FSM
  io.in.ready := lsExecUnit.io.in.ready
  lsExecUnit.io.wdata := io.wdata
  io.out.valid := lsExecUnit.io.out.valid

  io.dmem <> lsExecUnit.io.dmem
  io.out.bits := lsExecUnit.io.out.bits

  io.ioLoadAddrMisaligned := lsExecUnit.io.ioLoadAddrMisaligned
  io.ioStoreAddrMisaligned := lsExecUnit.io.ioStoreAddrMisaligned
}

// 具体操作LS行为的模型
class LSExecUnit extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new UnpipeLSUIO)

  val (valid, addr, ctrl) = (io.in.valid, io.in.bits.srcA, io.in.bits.ctrl)
  def access(valid: Bool, addr: UInt, ctrl: UInt): UInt = {
    this.valid := valid
    this.addr := addr
    this.ctrl := ctrl
    io.out.bits
  }

  def genWmask(addr: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> 0x1.U, // 0000_0001 << addr(2:0)
        "b01".U -> 0x3.U, // 0000_0011
        "b10".U -> 0xf.U, // 0000_1111
        "b11".U -> 0xff.U // 1111_1111
      )
    ) << addr(2, 0)
  }

  def genWdata(data: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> Fill(8, data(7, 0)),
        "b01".U -> Fill(4, data(15, 0)),
        "b10".U -> Fill(2, data(31, 0)),
        "b11".U -> data
      )
    )
  }

  def genWmask32(addr: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> 0x1.U, // 0001 << addr(1:0)
        "b01".U -> 0x3.U, // 0011
        "b10".U -> 0xf.U // 1111
      )
    ) << addr(1, 0)
  }

  def genWdata32(data: UInt, sizeEncode: UInt): UInt = {
    LookupTree(
      sizeEncode,
      List(
        "b00".U -> Fill(4, data(7, 0)),
        "b01".U -> Fill(2, data(15, 0)),
        "b10".U -> data
      )
    )
  }

  val dmem = io.dmem
  val addrLatch = RegNext(addr)
  val isStore = valid && LSUCtrl.isStore(ctrl)
  val partialLoad = !isStore && (ctrl =/= LSUCtrl.ld)

  val s_idle :: s_wait_resp :: s_partialLoad :: Nil = Enum(3)
  val state = RegInit(s_idle)

  switch(state) {
    is(s_idle) { when(dmem.req.fire) { state := s_wait_resp } }
    is(s_wait_resp) {
      when(dmem.resp.fire) { state := Mux(partialLoad, s_partialLoad, s_idle) }
    }
    is(s_partialLoad) { state := s_idle }
  }

  if (Settings.get("TraceLSU"))
    Debug(
      dmem.req.fire,
      "[LSU] addr %x, size %x, wdata_raw %x, isStore %x\n",
      addr,
      ctrl(1, 0),
      io.wdata,
      isStore
    )

  val size = ctrl(1, 0)
  val reqAddr =
    if (XLEN == 32) SignExt(addr, VAddrBits) else addr(VAddrBits - 1, 0)
  val reqWdata =
    if (XLEN == 32) genWdata32(io.wdata, size) else genWdata(io.wdata, size)
  val reqWmask =
    if (XLEN == 32) genWmask32(addr, size) else genWmask(addr, size)
  dmem.req.bits.apply(
    addr = reqAddr,
    len = 1.U,
    size = size,
    data = reqWdata,
    strb = reqWmask,
    last = true.B,
    write = isStore
  )
  dmem.req.valid := valid && (state === s_idle) && !io.ioLoadAddrMisaligned && !io.ioStoreAddrMisaligned
  dmem.resp.ready := true.B

  io.out.valid := Mux(
    /*state =/= s_idle || */ io.ioLoadAddrMisaligned || io.ioStoreAddrMisaligned,
    true.B,
    Mux(
      partialLoad,
      state === s_partialLoad,
      dmem.resp.fire && (state === s_wait_resp)
    )
  )
  io.in.ready := state === s_idle

  if (Settings.get("TraceLSU"))
    Debug(
      io.out.fire,
      "[LSU-EXECUNIT] state %x Resp %x lm %x sm %x\n",
      state,
      dmem.resp.fire,
      io.ioLoadAddrMisaligned,
      io.ioStoreAddrMisaligned
    )

  val rdata = dmem.resp.bits.data
  val rdataLatch = RegNext(rdata)
  // 在这里，因为地址为0x800002b4，后三位的读取为100
  // 所以截取了(63, 32)，使得0x00000001成为了0x0000____
  val rdataSel64 = LookupTree(
    addrLatch(2, 0),
    List(
      "b000".U -> rdataLatch(63, 0),
      "b001".U -> rdataLatch(63, 8),
      "b010".U -> rdataLatch(63, 16),
      "b011".U -> rdataLatch(63, 24),
      "b100".U -> rdataLatch(63, 32),
      "b101".U -> rdataLatch(63, 40),
      "b110".U -> rdataLatch(63, 48),
      "b111".U -> rdataLatch(63, 56)
    )
  )
  val rdataSel32 = LookupTree(
    addrLatch(1, 0),
    List(
      "b00".U -> rdataLatch(31, 0),
      "b01".U -> rdataLatch(31, 8),
      "b10".U -> rdataLatch(31, 16),
      "b11".U -> rdataLatch(31, 24)
    )
  )
  val rdataSel = if (XLEN == 32) rdataSel32 else rdataSel64
  // NOTE: 在这里，使用rdataLatch的情况是：不存在Cache，其访存返回的已经是截好的数据；如果是XLEN直接返回，则使用rdataSel32/rdtataSel64
  val rdataPartialLoad = LookupTree(
    ctrl,
    List(
      LSUCtrl.lb -> SignExt(rdataSel /*rdataLatch*/ (7, 0), XLEN),
      LSUCtrl.lh -> SignExt(rdataSel /*rdataLatch*/ (15, 0), XLEN),
      LSUCtrl.lw -> SignExt(rdataSel /*rdataLatch*/ (31, 0), XLEN),
      LSUCtrl.lbu -> ZeroExt(rdataSel /*rdataLatch*/ (7, 0), XLEN),
      LSUCtrl.lhu -> ZeroExt(rdataSel /*rdataLatch*/ (15, 0), XLEN),
      LSUCtrl.lwu -> ZeroExt(rdataSel /*rdataLatch*/ (31, 0), XLEN)
    )
  )
  val addrAligned = LookupTree(
    ctrl(1, 0),
    List(
      "b00".U -> true.B, // b
      "b01".U -> (addr(0) === 0.U), // h
      "b10".U -> (addr(1, 0) === 0.U), // w
      "b11".U -> (addr(2, 0) === 0.U) // d
    )
  )

  if (Settings.get("TraceLSU"))
    Debug(
      dmem.req.ready && dmem.resp.ready,
      "[LSU] state %x Write %x ReqAddr %x Fire %x Data %x AddrLatch %x RDataLatch %x RDataPartialLoad %x\n",
      state,
      dmem.req.bits.write,
      dmem.req.bits.addr,
      dmem.req.fire,
      dmem.req.bits.data,
      addrLatch(2, 0),
      rdataLatch,
      rdataPartialLoad
    )

  io.out.bits := Mux(partialLoad, rdataPartialLoad, rdata(XLEN - 1, 0))

  io.ioLoadAddrMisaligned := valid && !isStore && !addrAligned
  io.ioStoreAddrMisaligned := valid && isStore && !addrAligned

  if (Settings.get("TraceLSU"))
    Debug(
      io.ioLoadAddrMisaligned || io.ioStoreAddrMisaligned,
      "[EXCEPTION] misaligned addr detected\n"
    )
}
