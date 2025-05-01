package units

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import isa.mips._
import module.fu.mips._
import utils._

class MIPSDecoder(implicit val p: MarCoreConfig)
    extends MarCoreModule
    with HasMIPS_InstrType {
  implicit val moduleName: String = this.name
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CtrlFlowIO_MIPS))
    val out = Decoupled(new DecodeIO_MIPS)
    val isBranch = Output(Bool())
  })

  val instr = io.in.bits.instr // 承接instr
  val decodeList = ListLookup(
    instr,
    InstructionsMIPS.DecodeDefault,
    InstructionsMIPS.DecodeTable
  )
//	val instrType :: fuType :: fuCtrl :: Nil =
//		InstructionsMIPS.DecodeDefault.zip(decodeList).map{ case (instr, dec) => Mux(
//			hasIntr || io.in.bits.exceptionVec(instrPageFault) || io.out.bits.cf.exceptionVec(instrAccessFault),
//			instr, dec
//		)}
  val instrType :: fuType :: fuCtrl :: Nil =
    InstructionsMIPS.DecodeDefault.zip(decodeList).map { case (instr, dec) =>
      dec
    }
  io.out.bits := DontCare

  io.out.bits.ctrl.fuType := fuType
  io.out.bits.ctrl.fuCtrl := fuCtrl

  val isDelaySlot =
    RegEnable(Mux(ALUCtrl.isBru(instr), true.B, false.B), false.B, io.in.valid)
  io.out.bits.isDelaySlot := isDelaySlot

  val SrcTypeTable = List(
    InstrI -> (SrcType.reg, SrcType.imm),
    InstrR -> (SrcType.reg, SrcType.reg),
    InstrJ -> (SrcType.pc, SrcType.imm)
  )
  val srcAType = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._1)))
  val srcBType = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._2)))

  val (rs, rt, rd) = (instr(25, 21), instr(20, 16), instr(15, 11))

  val rfSrcA = rs
  val rfSrcB = rt
  val rfDest = rd

  io.out.bits.ctrl.rfSrcA := Mux(srcAType === SrcType.pc, 0.U, rfSrcA)
  io.out.bits.ctrl.rfSrcB := Mux(srcBType === SrcType.reg, rfSrcB, 0.U)
  io.out.bits.ctrl.rfWen := isrfWen(instrType)
  io.out.bits.ctrl.rfDest := Mux(
    isrfWen(instrType),
    rfDest,
    0.U
  ) // 如果不需要写入，那么就使用0，以避免出现意外的Forwarding

  io.out.bits.data := DontCare
  val imm = LookupTree(
    instrType,
    List(
      InstrI -> SignExt(instr(15, 0), XLEN),
      InstrR -> ZeroExt(instr(10, 6), XLEN),
      InstrJ -> ZeroExt(instr(25, 0), XLEN)
    )
  )
  io.out.bits.data.imm := imm

  /* 当call时，RV依赖特定的寄存器依赖 */
  when(fuType === FuType.bru) {
    def isLink(reg: UInt) = (reg === 31.U)
    when(isLink(rfDest) && fuCtrl === ALUCtrl.jalr) {
      io.out.bits.ctrl.fuCtrl := ALUCtrl.call
    }
    when(fuCtrl === ALUCtrl.jal) { io.out.bits.ctrl.fuCtrl := ALUCtrl.call }
    when(fuCtrl === ALUCtrl.jr) {
      when(isLink(rfSrcA)) { io.out.bits.ctrl.fuCtrl := ALUCtrl.ret }
    }
  }

  val NoSpecList = Seq(
    FuType.csr
  )

  val BlockList = Seq(
    FuType.mou
  )

  io.out.bits.ctrl.noSpecExec := NoSpecList
    .map(j => io.out.bits.ctrl.fuType === j)
    .reduce(_ || _) // 将列表中的每一个元素做比较，如果其中有任一个在列表中被找到，那么设置为true（reduce(_ || _)）
  io.out.bits.ctrl.isBlocked := (
    io.out.bits.ctrl.fuType === FuType.lsu && LSUCtrl.isAtom(
      io.out.bits.ctrl.fuCtrl
    ) ||
      BlockList.map(j => io.out.bits.ctrl.fuType === j).reduce(_ || _)
  )

  // output signals
  io.out.valid := io.in.valid
  io.in.ready := !io.in.valid || io.out.fire // && !hasIntr
  io.out.bits.cf <> io.in.bits

  // io.out.bits.ctrl.isMarCoreTrap := (instr === MarCoreTrap.TRAP) && io.in.valid
  // io.isBranch := VecInit(
  //   MIPS32_BJInstr.table.map(i => i._2.tail(1) === fuCtrl).toIndexedSeq
  // ).asUInt.orR &&
  //   fuType === FuType.bru
}

// class IDU(implicit val p: MarCoreConfig)
//     extends MarCoreModule
//     with HasInstrType {
//   val io = IO(new Bundle {
//     val in = Vec(2, Flipped(Decoupled(new CtrlFlowIO_MIPS)))
//     val out = Vec(2, Decoupled(new DecodeIO_MIPS))
//   })
//
//   val decoder1 = Module(new Decoder)
//   val decoder2 = Module(new Decoder)
//   io.in(0) <> decoder1.io.in
//   io.in(1) <> decoder2.io.in
//   io.out(0) <> decoder1.io.out
//   io.out(1) <> decoder2.io.out
//   if (!EnableMultiIssue) {
//     io.in(1).ready := false.B
//     decoder2.io.in.valid := false.B
//   }
// }
