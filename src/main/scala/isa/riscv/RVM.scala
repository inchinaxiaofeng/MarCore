package isa.riscv

import chisel3._
import chisel3.util._

import defs._
import module._
import module.fu._

object RV32M_Instr extends HasRISCV_InstrType {
  def MUL = BitPat("b0000001_?????_?????_000_?????_01100_11")
  def MULH = BitPat("b0000001_?????_?????_001_?????_01100_11")
  def MULHSU = BitPat("b0000001_?????_?????_010_?????_01100_11")
  def MULHU = BitPat("b0000001_?????_?????_011_?????_01100_11")
  def DIV = BitPat("b0000001_?????_?????_100_?????_01100_11")
  def DIVU = BitPat("b0000001_?????_?????_101_?????_01100_11")
  def REM = BitPat("b0000001_?????_?????_110_?????_01100_11")
  def REMU = BitPat("b0000001_?????_?????_111_?????_01100_11")

  val mul_table = Array(
    MUL -> List(InstrR, FuType.mulu, MulUCtrl.mul),
    MULH -> List(InstrR, FuType.mulu, MulUCtrl.mulh),
    MULHSU -> List(InstrR, FuType.mulu, MulUCtrl.mulhsu),
    MULHU -> List(InstrR, FuType.mulu, MulUCtrl.mulhu)
  )

  val div_table = Array(
    DIV -> List(InstrR, FuType.divu, DivUCtrl.div),
    DIVU -> List(InstrR, FuType.divu, DivUCtrl.divu),
    REM -> List(InstrR, FuType.divu, DivUCtrl.rem),
    REMU -> List(InstrR, FuType.divu, DivUCtrl.remu)
  )
  val table = mul_table ++ (if (HasDiv) div_table else Array.empty)
}

object RV64M_Instr extends HasRISCV_InstrType {
  def MULW = BitPat("b0000001_?????_?????_000_?????_01110_11")
  def DIVW = BitPat("b0000001_?????_?????_100_?????_01110_11")
  def DIVUW = BitPat("b0000001_?????_?????_101_?????_01110_11")
  def REMW = BitPat("b0000001_?????_?????_110_?????_01110_11")
  def REMUW = BitPat("b0000001_?????_?????_111_?????_01110_11")

  val mul_table = Array(
    MULW -> List(InstrR, FuType.mulu, MulUCtrl.mulw)
  )

  val div_table = Array(
    DIVW -> List(InstrR, FuType.divu, DivUCtrl.divw),
    DIVUW -> List(InstrR, FuType.divu, DivUCtrl.divuw),
    REMW -> List(InstrR, FuType.divu, DivUCtrl.remw),
    REMUW -> List(InstrR, FuType.divu, DivUCtrl.remuw)
  )
  val table = mul_table ++ (if (HasDiv) div_table else Array.empty)
}

object RVMInstr extends HasMarCoreParameter {
  val table =
    RV32M_Instr.table ++ (if (XLEN == 64) RV64M_Instr.table else Array.empty)
}

