//package module.ooo
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import utils._
//
//object physicalRFTools {
//	def getPRFAddr(robIndex: UInt, bank: UInt): UInt = {
//		Cat(robIndex, bank(0))
//	}
//}
//
//class ROB(implicit val p: MarCoreConfig) extends MarCoreModule with HasInstrType with HasBackendConst with HasRegFileParameter {
//	val io = IO(new Bundle {
//		val in = Vec(robWidth, Flipped(Decoupled(new DecodeIO)))
//		val brMaskIn = Input(Vec(robWidth, UInt(checkpointSize.W)))
//	})
//}
