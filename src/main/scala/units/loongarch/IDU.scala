package units

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import isa.loongarch._
import module.fu._
import utils._

class LoongArchDecoder(implicit val p: MarCoreConfig)
    extends MarCoreModule
    with HasLA32R_InstrType {
  implicit val moduleName: String = this.name
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CtrlFlowIO))
    val out = Decoupled(new DecodeIO)
    val isWFI = Output(Bool())
    val isBranch = Output(Bool())
  })

  val hasIntr = Wire(Bool()) // 中断信号
  val instr = io.in.bits.instr // 承接instr
  val decodeList =
    ListLookup(
      instr,
      InstructionsLA32R.DecodeDefault,
      InstructionsLA32R.DecodeTable
    )
  val instrType :: fuType :: fuCtrl :: Nil =
    InstructionsLA32R.DecodeDefault.zip(decodeList).map { case (instr, dec) =>
      Mux(
        hasIntr || io.in.bits.exceptionVec(instrPageFault) || io.out.bits.cf
          .exceptionVec(instrAccessFault),
        instr,
        dec
      )
    }

  io.out.bits := DontCare

  io.out.bits.ctrl.fuType := fuType
  io.out.bits.ctrl.fuCtrl := fuCtrl

  val SrcTypeTable = List(
    DJK -> (SrcType.reg, SrcType.reg),
    DJUk5 -> (SrcType.reg, SrcType.imm),
    DJSk12 -> (SrcType.reg, SrcType.imm),
    DJUk12 -> (SrcType.reg, SrcType.imm),
    DJSk16 -> (SrcType.reg, SrcType.imm),
    Ud15 -> (SrcType.reg, SrcType.reg), // none
    DSj20 -> (SrcType.pc, SrcType.imm),
    Sd10k16 -> (SrcType.pc, SrcType.imm),
    JSd5k16 -> (SrcType.reg, SrcType.imm),
    JUd5Sk12 -> (SrcType.imm, SrcType.imm) // 特例：你得按 preld 解码具体字段写法
  )

  val srcAType = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._1)))
  val srcBType = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._2)))

  val (rj, rk, rd) = (instr(14, 10), instr(9, 5), instr(4, 0))

  val rfSrcA = rj
  val rfSrcB = rk
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
      DJUk5 -> ZeroExt(instr(14, 10), XLEN),
      DJSk12 -> SignExt(instr(21, 10), XLEN),
      DJUk12 -> ZeroExt(instr(21, 10), XLEN),
      DJSk16 -> SignExt(instr(25, 10), XLEN),
      Ud15 -> ZeroExt(instr(14, 0), XLEN),
      DSj20 -> SignExt(instr(24, 5), XLEN),
      Sd10k16 -> SignExt(Cat(instr(9, 0), instr(25, 10)), XLEN),
      JSd5k16 -> SignExt(Cat(instr(4, 0), instr(25, 10)), XLEN),
      JUd5Sk12 -> ??? // 特例：你得按 preld 解码具体字段写法
    )
  )
  io.out.bits.data.imm := imm

  /*
	当call时，RV依赖特定的寄存器依赖
   */
  when(fuType === FuType.bru) {
    def isLink(reg: UInt) = (reg === 1.U || reg === 5.U)
    when(isLink(rfDest) && fuCtrl === BRUCtrl.jal) {
      io.out.bits.ctrl.fuCtrl := BRUCtrl.call
    }
    when(fuCtrl === BRUCtrl.jal) {
      when(isLink(rfSrcA)) { io.out.bits.ctrl.fuCtrl := BRUCtrl.ret }
      when(isLink(rfDest)) { io.out.bits.ctrl.fuCtrl := BRUCtrl.call }
    }
  }
  // special for LUI
  io.out.bits.ctrl.srcAType := Mux(
    instr(6, 0) === "b0110111".U,
    SrcType.reg,
    srcAType
  )
  io.out.bits.ctrl.srcBType := srcBType

  val NoSpecList = Seq(
    FuType.csr
  )

  val BlockList = Seq(
    FuType.mou
  )

//	io.out.bits.ctrl.isMarCoreTrap := (instr(31, 0) === MarCoreTrap.TRAP) && io.in.valid // 自陷判定，后面有了
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
  io.in.ready := !io.in.valid || io.out.fire && !hasIntr
  io.out.bits.cf <> io.in.bits

  // TODO: Intrupt
  val intrVec = WireInit(0.U(12.W)) // 中断的向量
  io.out.bits.cf.intrVec.zip(intrVec.asBools).map { case (x, y) =>
    x := y
  } // asBools转化成bool序列
  hasIntr := intrVec.orR // 每一位或，即有一个为true，则为true

  io.out.bits.cf.exceptionVec.map(_ := false.B)
  io.out.bits.cf.exceptionVec(
    illegalInstr
  ) := (instrType === InstrN && !hasIntr) && io.in.valid

  // TODO:
  // io.out.bits.ctrl.isMarCoreTrap := (instr === MarCoreTrap.TRAP) && io.in.valid
  // io.isWFI := (instr === Priviledged.WFI) && io.in.valid // 冻结芯片
  // io.isBranch := VecInit(
  //   LA32R_JumpInstr.table.map(i => i._2.tail(1) === fuCtrl).toIndexedSeq
  // ).asUInt.orR &&
  //   fuType === FuType.bru
}
