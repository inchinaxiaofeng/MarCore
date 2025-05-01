package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._
import top.Settings
import defs._
import blackbox._

// TODO 避免“破坏性别名”问题，对整个PC进行Hash，再用k位数值去寻址entry
// TODO 通过记录历史，解决复杂问题：BHR（自适应两级分支预测），BHR位数应等与序列里最长单字序列位数
// TODO 做全局的历史记录器，即GHR，代替BHR寻址PHT
// TODO 竞争的分支预测：CPHT

// 表示地址的数据结构，并定义了一些方法辅助操作和获取地址字段的值。
class TableAddr(val idxBits: Int) extends MarCoreBundle {
  // 填充字段位宽
  val padLen =
    if (Settings.get("IsRV32") || !Settings.get("EnableOutOfOrderExec")) 2
    else 3
  // 标签字段位宽
  def tagBits = VAddrBits - padLen - idxBits

  val tag = UInt(tagBits.W) // 标签
  val idx = UInt(idxBits.W) // 索引
  val pad = UInt(padLen.W) // 填充

  def fromUInt(x: UInt) = x.asTypeOf(UInt(VAddrBits.W)).asTypeOf(this)
  def getTag(x: UInt) = fromUInt(x).tag
  def getIdx(x: UInt) = fromUInt(x).idx
}

/** BTB 類型
  */
object BTBtype {

  /** Branch 類型, 表現爲轉移方向不確定, 地址確定
    */
  def B = "b00".U

  /** Jump 類型, 表現爲轉移方向確定, 地址確定
    */
  def J = "b01".U

  /** Indirect 類型, 表現爲轉移方向確定, 地址不確定
    *
    * @return
    */
  def I = "b10".U

  /** CallRet 類型, 函數調用
    *
    * @return
    */
  def R = "b11".U // return

  def apply() = UInt(2.W)
}

/** 分支預測單元更新包.
  *
  * 定義了用於更新分支預測單元信息的格式.
  */
class BPUUpdate extends MarCoreBundle {

  /** 拉高此信號, 更新有效
    */
  val valid = Output(Bool())

  /** 更新時需要提供 PC
    */
  val pc = Output(UInt(VAddrBits.W))

  /** 表示是否預測錯誤
    *
    * 之所以將這個信號與Valid分開設計, 恰恰在於幾乎所有的分支預測都需要記錄過去歷史.
    *
    * 因此, valid 信號拉高代表更新歷史, 而歷史中是否預測錯誤交個這個信號提供.
    */
  val isMissPredict = Output(Bool())

  /** 實際的跳轉目標
    */
  val actualTarget = Output(UInt(VAddrBits.W))

  /** 實際的跳轉方向(跳轉或不跳轉)
    */
  val actualTaken = Output(Bool())

  /** fu控制信號
    *
    * 用於判斷指令類型,即區分 Branch, Direct(立即數跳轉), Call, Ret, Indirect(Reg跳轉)
    *
    * 這些分裂的區別, 由OneHot類型與簡單編碼組成.
    *
    * 在使用時請訪問方法而不是整個匹配, 這樣可以將FuCtrl中沒有用到的位優化.
    */
  val fuCtrl = Output(FuCtrl())

  /** 寫入BTB的類型
    */
  val btbType = Output(BTBtype())
}

/* PC VAddrBits = 64
 * 63																			 0
 * xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx_xxxx
 */

class BPU_embedded extends MarCoreModule {
  val io = IO(new Bundle {
    val in = new Bundle { val pc = Flipped(Valid(UInt(VAddrBits.W))) }
    val out = new RedirectIO
    val flush = Input(Bool())
    val bpuUpdate = Flipped(new BPUUpdate)
  })

  val flush = BoolStopWatch(io.flush, io.in.pc.valid, startHighPriority = true)

  /* BTB */
  val NRbtb = 512
  val btbAddr = new TableAddr(log2Up(NRbtb))
  def btbEntry() = new Bundle {
    val tag = UInt(btbAddr.tagBits.W)
    val _type = UInt(2.W)
    val target = UInt(VAddrBits.W)
  }

  val btb = Module(
    new SRAMTemplate(
      btbEntry(),
      set = NRbtb,
      shouldReset = true,
      holdRead = true,
      singlePort = true
    )
  )
  btb.io.r.req.valid := io.in.pc.valid
  btb.io.r.req.bits.setIdx := btbAddr.getIdx(io.in.pc.bits)

  val btbRead = Wire(btbEntry())
  btbRead := btb.io.r.resp.data(0)
  // Since there is one cycle latency to read SyncReadMem,
  // we should latch the input PC for one cycle
  val pcLatch = RegEnable(io.in.pc.bits, io.in.pc.valid)
  val btbHit = btbRead.tag === btbAddr.getTag(pcLatch) && !flush && RegNext(
    btb.io.r.req.ready,
    init = false.B
  )

  // Local history
  /* BHR, local history branch history register.
   * Entry: 4 bits, which equals 16 entry per pht.
   * NR: NRbtb
   */
  val LNRpht = 16
  val bhr = Mem(NRbtb, UInt(log2Up(LNRpht).W))
  val lphts = Mem(NRbtb * LNRpht, UInt(2.W))
  val lphtsAddr =
    Cat(btbAddr.getIdx(io.in.pc.bits), bhr.read(btbAddr.getIdx(io.in.pc.bits)))
  val lphtsTaken = RegEnable(lphts.read(lphtsAddr)(1), io.in.pc.valid)

  // Global history
  /* GHR, global history branch history register
   * Entry: 6 bits, which equals 64 entry per pht
   */
  val GNRpht = 64
  val ghr = Mem(1, UInt(log2Up(GNRpht).W))
  val gphts = Mem(NRbtb * GNRpht, UInt(2.W))
  val gphtsAddr = Cat(btbAddr.getIdx(io.in.pc.bits), ghr.read(0.U))
  val gphtsTaken = RegEnable(gphts.read(gphtsAddr)(1), io.in.pc.valid)

  // Choice PHT, 1 is global, 0 is local
  val cpht = Mem(NRbtb, UInt(2.W))
  val cphtAddr = btbAddr.getIdx(io.in.pc.bits)
  val cphtGlobal = RegEnable(cpht.read(cphtAddr)(1), io.in.pc.valid)
  // TODO 这里的REG是什么啊

  val phtTaken = Mux(cphtGlobal, gphtsTaken, lphtsTaken)

  /* RAS */
  val NRras = 16
  val ras = Mem(NRras, UInt(VAddrBits.W))
  val sp = Counter(NRras)
  val rasTarget = RegEnable(ras.read(sp.value), io.in.pc.valid)

  /* update */
  //	val req = WireInit(0.U.asTypeOf(new BPUUpdate))
  val btbWrite = WireInit(0.U.asTypeOf(btbEntry()))

  btbWrite.tag := btbAddr.getTag(io.bpuUpdate.pc)
  btbWrite.target := io.bpuUpdate.actualTarget
  btbWrite._type := io.bpuUpdate.btbType
  // NOTE: We only update BTB at a miss prediction.
  // If a miss prediction is found, the pipeline will be flushed in the next cycle.
  // Therefore it is safe to use single port SRAM implement BTB,
  // since write request have higher priority than read request.
  // Again, since the pipeline will be flushed
  // in the next cycle, the read request will be useless.
  btb.io.w.req.valid := io.bpuUpdate.isMissPredict && io.bpuUpdate.valid
  btb.io.w.req.bits.setIdx := btbAddr.getIdx(io.bpuUpdate.pc)
  btb.io.w.req.bits.data := btbWrite

  val lphtsAddrUpdt = Cat(
    btbAddr.getIdx(io.bpuUpdate.pc),
    bhr.read(btbAddr.getIdx(io.bpuUpdate.pc))
  )
  val gphtsAddrUpdt = Cat(btbAddr.getIdx(io.bpuUpdate.pc), ghr.read(0.U))
  val cphtAddrUpdt = btbAddr.getIdx(io.bpuUpdate.pc)

  val cnt = RegNext(
    Mux(
      cpht.read(cphtAddrUpdt)(1),
      gphts.read(gphtsAddrUpdt),
      lphts.read(lphtsAddrUpdt)
    )
  )
  val cphtCnt = RegNext(cpht.read(cphtAddrUpdt))

  val reqLatch = RegNext(io.bpuUpdate)
  val btbIdxReq = btbAddr.getIdx(reqLatch.pc)

  val lphtsAddrReq =
    Cat(btbAddr.getIdx(reqLatch.pc), bhr.read(btbAddr.getIdx(reqLatch.pc)))
  val gphtsAddrReq = Cat(btbAddr.getIdx(reqLatch.pc), ghr.read(0.U))
  val cphtAddrReq = btbAddr.getIdx(reqLatch.pc)
  val cphtGlobalReq = cpht.read(cphtAddrReq)(1)

  when(reqLatch.valid && BRUCtrl.isBranch(reqLatch.fuCtrl)) {
    val taken = reqLatch.actualTaken
    val newCnt = Mux(taken, cnt + 1.U, cnt - 1.U)
    val newCphtCnt = Mux(
      cphtGlobalReq,
      Mux(
        taken,
        Mux(cphtCnt === "b11".U, cphtCnt, cphtCnt + 1.U),
        cphtCnt - 1.U
      ),
      Mux(
        taken,
        Mux(cphtCnt === "b00".U, cphtCnt, cphtCnt - 1.U),
        cphtCnt + 1.U
      )
    )
    cpht.write(cphtAddrReq, newCphtCnt)
    val wen = (taken && (cnt =/= "b11".U)) || (!taken && (cnt =/= "b00".U))
    when(cphtGlobalReq) {
      when(wen) {
        ghr.write(btbIdxReq, Cat(ghr.read(0.U)(log2Up(GNRpht) - 2, 0), taken))
        gphts.write(gphtsAddrReq, newCnt)
      }
    }.elsewhen(!cphtGlobalReq) {
      when(wen) {
        bhr.write(
          btbIdxReq,
          Cat(bhr.read(btbIdxReq)(log2Up(LNRpht) - 2, 0), taken)
        )
        lphts.write(lphtsAddrReq, newCnt)
      }
    }
  }
  when(io.bpuUpdate.valid) {
    when(io.bpuUpdate.fuCtrl === BRUCtrl.call) {
      ras.write(sp.value + 1.U, io.bpuUpdate.pc + 4.U)
      sp.value := sp.value + 1.U
    }.elsewhen(io.bpuUpdate.fuCtrl === BRUCtrl.ret) {
      sp.value := sp.value - 1.U
    }
  }

  val flushBTB = WireInit(false.B)
  val flushTLB = WireInit(false.B)

  io.out.target := Mux(btbRead._type === BTBtype.R, rasTarget, btbRead.target)
  io.out.valid := btbHit && Mux(btbRead._type === BTBtype.B, phtTaken, true.B)
  io.out.rtype := 0.U
  if (Settings.get("Statistic")) {
    val statistic_pred_choice = Module(new STATISTIC_PRED_CHOICE)
    statistic_pred_choice.io.clk := clock
    statistic_pred_choice.io.rst := reset
    statistic_pred_choice.io.pGlobal := io.out.valid && /*cpht.read(cphtAddr) === 0.U*/ cphtGlobal
    statistic_pred_choice.io.pLocal := io.out.valid && /*cpht.read(cphtAddr) =/= 0.U*/ !cphtGlobal
  }
}

class BPU_inorder extends MarCoreModule {
  val io = IO(new Bundle {
    val in = new Bundle { val pc = Flipped(Valid(UInt(VAddrBits.W))) }
    val out = new RedirectIO
    val flush = Input(Bool())
    val brIdx = Output(UInt(3.W))
    val crosslineJump = Output(Bool())
    val bpuUpdate = Flipped(new BPUUpdate)
  })

  val flush = BoolStopWatch(io.flush, io.in.pc.valid, startHighPriority = true)

  // BTB
  val NRbtb = 512
  val btbAddr = new TableAddr(log2Up(NRbtb))
  def btbEntry() = new Bundle {
    val tag = UInt(btbAddr.tagBits.W)
    val _type = UInt(2.W)
    val target = UInt(VAddrBits.W)
    val brIdx = UInt(3.W)
    val valid = Bool()
  }

  val btb = Module(
    new SRAMTemplate(
      btbEntry(),
      set = NRbtb,
      shouldReset = true,
      holdRead = true,
      singlePort = true
    )
  )
  // flush BTB when executing fence.i
//	val flushBTB = WireInit(false.B)
//	val flushTLB = WireInit(false.B)
  btb.io.r.req.valid := io.in.pc.valid
  btb.io.r.req.bits.setIdx := btbAddr.getIdx(io.in.pc.bits)

  val btbRead = Wire(btbEntry())
  btbRead := btb.io.r.resp.data(0)
  // Since there is one cycle latency to read SyncReadMem,
  // we should latch the input pc for one cycle
  val pcLatch = RegEnable(io.in.pc.bits, io.in.pc.valid)
  val btbHit = btbRead.valid && btbRead.tag === btbAddr.getTag(
    pcLatch
  ) && !flush && RegNext(btb.io.r.req.ready, init = false.B) && !(pcLatch(
    1
  ) && btbRead.brIdx(0))

  val crosslineJump = btbRead.brIdx(2) && btbHit
  io.crosslineJump := crosslineJump

  /* PHT, 2 bits */
  val phtLEN = 2

  // Local history
  /* BHR, local history branch history register.
   * Entry: 4 bits, which equals 16 entry per pht.
   * NR: NRbtb
   */
  val LNRpht = 16
  val bhr = Mem(NRbtb, UInt(log2Up(LNRpht).W))
  val lphts = Mem(NRbtb * LNRpht, UInt(phtLEN.W))
  val lphtsAddr =
    Cat(btbAddr.getIdx(io.in.pc.bits), bhr.read(btbAddr.getIdx(io.in.pc.bits)))
  val lphtsTaken = RegEnable(lphts.read(lphtsAddr)(phtLEN - 1), io.in.pc.valid)

  // Global history
  /* GHR, global history branch history register
   * Entry: 6 bits, which equals 64 entry per pht
   */
  val GNRpht = 64
  val ghr = Mem(1, UInt(log2Up(GNRpht).W))
  val gphts = Mem(NRbtb * GNRpht, UInt(phtLEN.W))
  val gphtsAddr = Cat(btbAddr.getIdx(io.in.pc.bits), ghr.read(0.U))
  val gphtsTaken = RegEnable(gphts.read(gphtsAddr)(phtLEN - 1), io.in.pc.valid)

  // Choice PHT, 1 is global, 0 is local
  val cpht = Mem(NRbtb, UInt(phtLEN.W))
  val cphtAddr = btbAddr.getIdx(io.in.pc.bits)
  val cphtGlobal = RegEnable(cpht.read(cphtAddr)(phtLEN - 1), io.in.pc.valid)

  val phtTaken = Mux(cphtGlobal, gphtsTaken, lphtsTaken)

  /* RAS */
  val NRras = 16
  val rasResetState = RegInit(true.B)
  val (rasResetIdx, rasResetFinish) = Counter(rasResetState, NRras)
  when(rasResetFinish) { rasResetState := false.B }

  val ras = Mem(NRras, UInt(VAddrBits.W))
  val sp = Counter(NRras)
  val rasRead = Mux(rasResetState, 0.U, ras.read(sp.value))
  val rasTarget = RegEnable(rasRead, io.in.pc.valid)

  /* update */
  val btbWrite = WireInit(0.U.asTypeOf(btbEntry()))

//	Debug(io.bpuUpdate.valid, "[BTBUP] pc=%x tag=%x index=%x brIdx=%x tgt=%x type=%x\n",
//		io.bpuUpdate.pc, btbAddr.getIdx(io.bpuUpdate.pc), btbAddr.getIdx(io.bpuUpdate.pc),
//		Cat(io.bpuUpdate.pc(1), ~io.bpuUpdate.pc(1), io.bpuUpdate.actualTarget, io.bpuUpdate.btbType))

  btbWrite.tag := btbAddr.getTag(io.bpuUpdate.pc)
  btbWrite.target := io.bpuUpdate.actualTarget
  btbWrite._type := io.bpuUpdate.btbType
//	btbWrite.brIdx := Cat(io.bpuUpdate.pc(2,0)==="h6".U && !io.bpuUpdate.isRVC, io.bpuUpdate.pc(1), ~io.bpuUpdate.pc(1))
  btbWrite.brIdx := Cat(
    io.bpuUpdate.pc(2, 0) === "h6".U,
    io.bpuUpdate.pc(1),
    ~io.bpuUpdate.pc(1)
  )
  btbWrite.valid := true.B

  btb.io.w.req.valid := io.bpuUpdate.isMissPredict && io.bpuUpdate.valid
  btb.io.w.req.bits.setIdx := btbAddr.getIdx(io.bpuUpdate.pc)
  btb.io.w.req.bits.data := btbWrite

  val lphtsAddrUpdt = Cat(
    btbAddr.getIdx(io.bpuUpdate.pc),
    bhr.read(btbAddr.getIdx(io.bpuUpdate.pc))
  )
  val gphtsAddrUpdt = Cat(btbAddr.getIdx(io.bpuUpdate.pc), ghr.read(0.U))
  val cphtAddrUpdt = btbAddr.getIdx(io.bpuUpdate.pc)

  val cnt = RegNext(
    Mux(
      cpht.read(cphtAddrUpdt)(phtLEN - 1),
      gphts.read(gphtsAddrUpdt),
      lphts.read(lphtsAddrUpdt)
    )
  )
  val reqLatch = RegNext(io.bpuUpdate)
  val btbIdxReq = btbAddr.getIdx(reqLatch.pc)

  val lphtsAddrReq =
    Cat(btbAddr.getIdx(reqLatch.pc), bhr.read(btbAddr.getIdx(reqLatch.pc)))
  val gphtsAddrReq =
    Cat(btbAddr.getIdx(reqLatch.pc), ghr.read(btbAddr.getIdx(reqLatch.pc)))
  val cphtAddrReq = btbAddr.getIdx(reqLatch.pc)

  when(reqLatch.valid && BRUCtrl.isBranch(reqLatch.fuCtrl)) {
    val taken = reqLatch.actualTaken
    val newCnt = Mux(taken, cnt + 1.U, cnt - 1.U)
    cpht.write(btbIdxReq, 1.U)
    val wen = (taken && (cnt =/= "b11".U)) || (!taken && (cnt =/= "b00".U))
    when(wen && cpht.read(cphtAddrReq)(phtLEN - 1)) {
      ghr.write(
        btbIdxReq,
        Cat(ghr.read(btbIdxReq)(log2Up(GNRpht) - 2, 0), taken)
      )
      gphts.write(gphtsAddrReq, newCnt)
    }.elsewhen(wen && !cpht.read(cphtAddrReq)(phtLEN - 1)) {
      bhr.write(
        btbIdxReq,
        Cat(bhr.read(btbIdxReq)(log2Up(LNRpht) - 2, 0), taken)
      )
      lphts.write(lphtsAddrReq, newCnt)
    }
  }

  val rasIdx = Mux(rasResetState, rasResetIdx, sp.value + 1.U)
//	val rasWdata = Mux(rasResetState, 0.U, req.pc = Mux(io.bpuUpdate.isRVC, 2.U, 4.U))
  val rasWdata = Mux(rasResetState, 0.U, io.bpuUpdate.pc + 4.U)
  val rasWen =
    rasResetState || (io.bpuUpdate.valid && (io.bpuUpdate.valid && (io.bpuUpdate.fuCtrl === BRUCtrl.call)))
  when(rasWen) { ras.write(rasIdx, rasWdata) }
  when(io.bpuUpdate.valid) {
    when(io.bpuUpdate.fuCtrl === BRUCtrl.call) {
      //		ras.write(sp.value + 1.U, io.bpuUpdate.pc + 4.U)
      sp.value := sp.value + 1.U
    }.elsewhen(io.bpuUpdate.fuCtrl === BRUCtrl.ret) {
      sp.value := sp.value - 1.U
    }
  }
  io.out.target := Mux(btbRead._type === BTBtype.R, rasTarget, btbRead.target)
  io.brIdx := btbRead.brIdx & Cat(true.B, crosslineJump, Fill(2, io.out.valid))
  io.out.valid := btbHit && Mux(
    btbRead._type === BTBtype.B,
    phtTaken,
    true.B && rasTarget =/= 0.U
  )
  io.out.rtype := 0.U
}
