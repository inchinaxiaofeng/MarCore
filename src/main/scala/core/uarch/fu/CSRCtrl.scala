package core.uarch.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._

object CSRCtrl {

  /** 跳转指令. 用于在对应CSR中, 需要进行跳转的时候指定(Syscall, Ecall, xRet等)
    *
    * @return
    */
  def jmp = "b000".U

  /** 对 bitmask 指定的位同时把 CSR 与 rs1 原子互换
    *
    * @return
    */
  def xchg = "b000".U

  /** 写整寄存器（R/W）
    *   - 旧 CSR→rd，CSR←rs1
    *
    * @return
    */
  def wrt = "b001".U

  /** 写掩码置位（RS）
    *   - CSRRS: 旧 CSR→rd，CSR← CSR_old | rs1
    *
    * @return
    */
  def set = "b010".U
  def clr = "b011".U
  def wrti = "b101".U
  def seti = "b110".U
  def clri = "b111".U
}
