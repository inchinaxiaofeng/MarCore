//package device
//
//import chisel3._
//import chisel3.util._
//
//import bus.axi4._
//import defs._
//import utils._
//import top._
//
//class AXI4RAM[T <: AXI4Lite](_type: T = new AXI4, memByte: Long,
//useBlackBox: Boolean = false) extends AXI4SlaveModule(_type) with HasMarCoreParameter {
//	def index(addr: UInt) = addr(log2Ceil(memByte) - 1, log2Ceil(DataBytes))
//	def inRange(idx: UInt) = idx < (memByte / 8).U
//
//	val wIdx = index(waddr) + writeBeatCnt
//	val rIdx = index(raddr) + readBeatCnt
//	val wen = in.w.fire && inRange(wIdx)
//
//	val rdata = if (useBlackBox) {
//		val mem = DifftestMem(memByte, 8)
//		when (wen) {
//			mem.write(
//				addr = wIdx,
//				data = in.w.bits.data.asTypeOf(Vec(DataBytes, UInt(8.W)))
//				mask = in.w.bits.strb.asBools
//			)
//		}
//		mem.readAndHold(rIdx, ren).asUInt
//	} else {
//		val mem = Mem(memByte / DataBytes, Vec(DataBytes, UInt(8.W)))
//
//		val wdata = VecInit.tabulate(DataBytes) { i => in.w.bits.data(8*(i+1)-1, 8*i) }
//		when (wen) { mem.write(wIdx, wdata, in.w.bits.strb.asBools) }
//
//		RegEnable(Cat(mem.read(rIdx).reverse), ren)
//	}
//
//	in.r.bits.data := rdata
//}
