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
package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import config._
import utils._
import utils.fu._

import core.isa.instr.RV32I_BRUInstr
import core.uarch.interfaces._
import core.uarch.fu.BRUCtrl
import core.uarch.branch.BPUUpdate

class BRUIO extends FuCtrlIO {
  val cfIn = Flipped(new CtrlFlowIO)

  /** 用於在錯誤發生時進行重定向
    */
  val redirect = new RedirectIO

  /** 提供用於給PC計算的偏移值
    */
  val imm = Input(UInt(XLEN.W))
  val bpuUpdate = new BPUUpdate
}

/** 公版 BRU
  *
  * 由於 MIPS 需要分支延遲槽的原因, 寫回地址需要爲8.
  *
  * 評估後認爲在公版中提供支持會降低代碼可讀性, 因此在此不支持.
  */
class BRU extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new BRUIO)

  val (valid, srcA, srcB, ctrl) =
    (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
  def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
    this.valid := valid
    this.srcA := srcA
    this.srcB := srcB
    this.ctrl := ctrl
    io.out.bits
  }

  val isBranch = BRUCtrl.isBranch(ctrl)
  val isIndirect = BRUCtrl.isIndirect(ctrl)

  // 通過減法做比較, Branch類型時做減法
  val (adderRes, adderCarry) =
    AdderGen(XLEN, srcA, (srcB ^ Fill(XLEN, isBranch)), isBranch)

  // 靜態地址
  val starget = io.cfIn.pc + 4.U

  // 動態地址
  // 跳轉目標有兩個來源, 直接目標與間接目標
  val directTarget = (io.cfIn.pc + io.imm)(XLEN - 1, 0)

  val sltu = !adderCarry
  val xorRes = srcA ^ srcB
  val slt = xorRes(XLEN - 1) ^ sltu

  val branchOpTable = List(
    BRUCtrl.getEncoded(BRUCtrl.beq) -> !xorRes.orR,
    BRUCtrl.getEncoded(BRUCtrl.blt) -> slt,
    BRUCtrl.getEncoded(BRUCtrl.ble) -> (slt | ~xorRes.orR),
    BRUCtrl.getEncoded(BRUCtrl.bltu) -> sltu
  )

  // 分支方向
  val taken = LookupTree(BRUCtrl.getEncoded(ctrl), branchOpTable) ^ BRUCtrl
    .isInvert(ctrl)
  // 分支目標
  val target = Mux(isBranch, directTarget, adderRes)(VAddrBits - 1, 0)

  val predictWrong = io.redirect.target =/= io.cfIn.pnpc

  // 當實際計算出的地址與預測地址不一致時, 預測錯誤
  io.redirect.valid := valid && predictWrong
  val redirectRtype = if (EnableOutOfOrderExec) 1.U else 0.U
  io.redirect.rtype := redirectRtype
  io.redirect.target := Mux(!taken && isBranch, starget, target)

  io.bpuUpdate.valid := valid
  io.bpuUpdate.pc := io.cfIn.pc
  io.bpuUpdate.isMissPredict := predictWrong
  io.bpuUpdate.actualTarget := target
  io.bpuUpdate.actualTaken := taken
  io.bpuUpdate.fuCtrl := ctrl
  io.bpuUpdate.btbType := LookupTree(
    ctrl,
    RV32I_BRUInstr.bruCtrl2BtbTypeTable
  )

  val stargetSign =
    (SignExt(io.cfIn.pc, AddrBits) + 4.U(XLEN - 1, 0))
  // mark redirect type as speculative exec fix
  // may be can be moved to ISU to calculate pc + 4
  // this is actually for jal and jalr to write pc + 4/2 to rd
  io.out.bits := stargetSign
  io.in.ready := io.out.ready
  io.out.valid := valid

  // ==== LogOut ====
  if (BaseConfig.get("LogALU")) {
    Debug(
      io.in.fire,
      "[In  Fire] tgt %x valid %d npc %x pdwrong %x\n",
      io.redirect.target,
      io.redirect.valid,
      io.cfIn.pnpc,
      predictWrong
    )
    Trace(
      io.in.fire,
      "taken %d addrRes %x srcA %x srcB %x ctrl %x\n",
      taken,
      adderRes,
      srcA,
      srcB,
      ctrl
    )
    Trace(
      valid,
      "[BPW] pc %x tgt %x npc %x pdWrong %x type %x%x%x%x%x%x\n",
      io.cfIn.pc,
      io.redirect.target,
      io.cfIn.pnpc,
      predictWrong,
      isBranch,
      ctrl === BRUCtrl.j,
      ctrl === BRUCtrl.jal,
      ctrl === BRUCtrl.jr,
      ctrl === BRUCtrl.jalr,
      ctrl === BRUCtrl.call,
      ctrl === BRUCtrl.ret
    )
  }
}
