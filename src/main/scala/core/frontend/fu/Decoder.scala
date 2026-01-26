/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
package core.frontend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import utils.fu._
import core.isa.{HasInstrType, Instructions, FuType, SrcType}
import core.uarch.fu.{ALUCtrl, BRUCtrl, LSUCtrl}
import core.uarch.interfaces._
import core.isa.csr.{HasExceptionNO, HasCSRConst}
import core.isa.instr.RV32I_BRUInstr

/** DecodeIO Bundle
  */
class DeIO(implicit val p: MarCoreConfig) extends MarCoreBundle {
  val in = Flipped(Decoupled(new CtrlFlowIO))
  val out = Decoupled(new DecodeIO)
  val isWFI = Output(Bool())
  val isBranch = Output(Bool())
}

/** 解码单元公共接口
  */
trait HasDeIO {
  implicit val p: MarCoreConfig
  val io = IO(new DeIO)
}

class Decoder(implicit val p: MarCoreConfig)
    extends MarCoreModule
    with HasDeIO // 获得Decode标准借口
    with HasInstrType // 获得指令类型
    with HasExceptionNO {
  implicit val moduleName: String = this.name // 提供模块名字

  val hasIntr = Wire(Bool()) // 中断信号
  val instr = io.in.bits.instr // 承接instr
  val decodeList = // 解码查询列表
    ListLookup(
      instr,
      Instructions.DecodeDefault,
      Instructions.DecodeTable
    )
  val instrType :: fuType :: fuCtrl :: Nil = // 解码后的控制信号
    Instructions.DecodeDefault.zip(decodeList).map { case (instr, dec) =>
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

  // 指令类型与对应源操作数
  val SrcTypeTable = List(
    InstrI -> (SrcType.reg, SrcType.imm),
    InstrR -> (SrcType.reg, SrcType.reg),
    InstrS -> (SrcType.reg, SrcType.reg),
    InstrB -> (SrcType.reg, SrcType.reg),
    InstrU -> (SrcType.pc, SrcType.imm),
    InstrJ -> (SrcType.pc, SrcType.imm)
  )
  val srcAType = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._1)))
  val srcBType = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._2)))

  val (rfSrcA, rfSrcB, rfDest) = (instr(19, 15), instr(24, 20), instr(11, 7))

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
      InstrI -> SignExt(instr(31, 20), XLEN),
      InstrS -> SignExt(Cat(instr(31, 25), instr(11, 7)), XLEN),
      InstrB -> SignExt(
        Cat(instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W)),
        XLEN
      ),
      InstrU -> SignExt(Cat(instr(31, 12), 0.U(12.W)), XLEN),
      InstrJ -> SignExt(
        Cat(instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W)),
        XLEN
      )
    )
  )

  io.out.bits.data.imm := imm

// --- 微架构相关 ---

  /* NOTE: RAS.
   * RAS需要区分Call和Ret.
   * 当 Call 时，RV依赖特定的寄存器依赖
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

  // NOTE: LUI
  io.out.bits.ctrl.srcAType := Mux(
    instr(6, 0) === "b0110111".U,
    SrcType.reg,
    srcAType
  )
  io.out.bits.ctrl.srcBType := srcBType

  // NOTE: 禁止乱序执行的单元列表
  // 等待之前的指令全部提交
  val NoSpecList = Seq(
    FuType.csr,
    FuType.lsu
  )

  // NOTE: 等待当前指令提交后才进行取指
  val BlockList = Seq(
    FuType.mou
  )

//	io.out.bits.ctrl.isMarCoreTrap := (instr(31, 0) === MarCoreTrap.TRAP) && io.in.valid // 自陷判定，后面有了
  // NOTE: 批量黑名单/白名单匹配, 详情见README
  // 将列表中的每一个元素做比较，如果其中有任一个在列表中被找到，那么设置为true（reduce(_ || _)）
  io.out.bits.ctrl.noSpecExec := NoSpecList
    .map(j => io.out.bits.ctrl.fuType === j)
    .reduce(_ || _)

  // 原子指令需要Block
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

  // io.out.bits.ctrl.isMarCoreTrap := (instr === MarCoreTrap.TRAP) && io.in.valid
  io.isWFI := false.B // (instr === Priviledged.WFI) && io.in.valid // 冻结芯片
  io.isBranch := VecInit(
    RV32I_BRUInstr.table.map(i => i._2.tail(1) === fuCtrl).toIndexedSeq
  ).asUInt.orR &&
    fuType === FuType.bru
}
