package module.fu.mips

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._
import top.Settings
import defs._

// TODO 避免“破坏性别名”问题，对整个PC进行Hash，再用k位数值去寻址entry
// TODO 通过记录历史，解决复杂问题：BHR（自适应两级分支预测），BHR位数应等与序列里最长单字序列位数
// TODO 做全局的历史记录器，即GHR，代替BHR寻址PHT
// TODO 竞争的分支预测：CPHT

// 表示地址的数据结构，并定义了一些方法辅助操作和获取地址字段的值。
class TableAddr(val idxBits: Int) extends MarCoreBundle {
	// 填充字段位宽
	val padLen = if (Settings.get("IsRV32") || !Settings.get("EnableOutOfOrderExec")) 2 else 3
	// 标签字段位宽
	def tagBits = VAddrBits - padLen - idxBits

	val tag = UInt(tagBits.W)	// 标签
	val idx = UInt(idxBits.W)	// 索引
	val pad = UInt(padLen.W)	// 填充

	def fromUInt(x: UInt) = x.asTypeOf(UInt(VAddrBits.W)).asTypeOf(this)
	def getTag(x: UInt) = fromUInt(x).tag
	def getIdx(x: UInt) = fromUInt(x).idx
}

object BTBtype {
	def B = "b00".U // branch
	def J = "b01".U // jump
	def I = "b10".U // indirect
	def R = "b11".U // return

	def apply() = UInt(2.W)
}

class BPUUpdate extends MarCoreBundle {
	val valid = Output(Bool())
	val pc = Output(UInt(VAddrBits.W))
	val isMissPredict = Output(Bool())
	val actualTarget = Output(UInt(VAddrBits.W))
	val actualTaken = Output(Bool())
	val fuCtrl = Output(FuCtrl())
	val btbType = Output(BTBtype())
}

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

	val btb = Module(new SRAMTemplate(btbEntry(), set = NRbtb, shouldReset = true, holdRead = true, singlePort = true))
	btb.io.r.req.valid := io.in.pc.valid
	btb.io.r.req.bits.setIdx := btbAddr.getIdx(io.in.pc.bits)

	val btbRead = Wire(btbEntry())
	btbRead := btb.io.r.resp.data(0)
	// Since there is one cycle latency to read SyncReadMem,
	// we should latch the input PC for one cycle
	val pcLatch = RegEnable(io.in.pc.bits, io.in.pc.valid)
	val	btbHit = btbRead.tag === btbAddr.getTag(pcLatch) && !flush && RegNext(btb.io.r.req.ready, init = false.B)

	/* PHT */
	val pht = Mem(NRbtb, UInt(2.W))
	val phtTaken = RegEnable(pht.read(btbAddr.getIdx(io.in.pc.bits))(1), io.in.pc.valid)

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

	val cnt = RegNext(pht.read(btbAddr.getIdx(io.bpuUpdate.pc)))
	val reqLatch = RegNext(io.bpuUpdate)
	when (reqLatch.valid && ALUCtrl.isBranch(reqLatch.fuCtrl)) {
		val taken = reqLatch.actualTaken
		val newCnt = Mux(taken, cnt + 1.U, cnt - 1.U)
		val wen = (taken && (cnt =/= "b11".U)) || (!taken && (cnt =/= "b00".U))
		when (wen) {
			pht.write(btbAddr.getIdx(reqLatch.pc), newCnt)
		}
	}
	when (io.bpuUpdate.valid) {
		when (io.bpuUpdate.fuCtrl === ALUCtrl.call) {
			ras.write(sp.value + 1.U, io.bpuUpdate.pc + 4.U)
			sp.value := sp.value + 1.U
		}.elsewhen (io.bpuUpdate.fuCtrl === ALUCtrl.ret) {
			sp.value := sp.value - 1.U
		}
	}

	val flushBTB = WireInit(false.B)
	val flushTLB = WireInit(false.B)
	
	io.out.target := Mux(btbRead._type === BTBtype.R, rasTarget, btbRead.target)
	io.out.valid := btbHit && Mux(btbRead._type === BTBtype.B, phtTaken, true.B)
	io.out.rtype := 0.U
}
