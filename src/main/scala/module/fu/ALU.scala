package module.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import module.fu.BPUUpdate
import isa.riscv._
import top.Settings
import blackbox._

/** 通用化的 ALU 編碼結構。通過設計 ALU 指令，將ISA與架構實現分離。
  *
  * ALU Ctrl 的位寬將會影響流水間寄存器的開銷, 因此要節約位寬,採取了編碼的方式進行.
  *
  * 但是爲了避免編碼解碼開銷, 在可以的情況下, 儘量賦予每一個位獨特的意義.
  *
  * 考慮到主要的RISC架構中,都將 W 后缀設計爲 64-bit 下的特产，专为处理 legacy 32-bit 数据而设，32 位下無效或不存在.
  *
  * 因此, 我們在這裏保持一致, W 後綴只有在 64-bit 下才有意義, 32-bit 下不應該使用
  *
  * @note
  *   不同架構不需要實現全部指令, 僅僅對用到的進行處理即可. 不實現就不會造成面積開銷的浪費.
  *
  * @param `[6]`
  *   Adder sub bit. 加法器控制位, 可以通過這一位判斷當前是加還是減. 當拉高時, 加法器將進行減法.
  *
  * @param `[5]`
  *   Word bit. XLEN == 64 時, 當這一位拉高時，指 Word 數據類型. XLEN == 32時, 這一位被拋棄.
  *
  * @param `[4]`
  *   Unsigned bit. 無符號標誌位. 當這一位拉高時, 對 可拓展原數據 進行0拓展, 否則將會使用符號拓展
  *
  * @param `[3,0]`
  *   Encoded type bit. 編碼後類型, 用於在無法通過其他位分別時, 區分不同指令. 當編碼全爲 0 時用於區分不需要進行匹配
  */
object ALUCtrl {
  def add = "b000_0000".U
  def addu = "b001_0000".U
  def sub = "b100_0000".U
  def addw = "b010_0000".U
  def adduw = "b011_0000".U
  def subw = "b110_0000".U

  def sll = "b000_0001".U
  def srl = "b000_0010".U
  def sra = "b000_0011".U
  def sllw = "b010_0001".U // CHECK
  def srlw = "b010_0010".U // CHECK
  def sraw = "b010_0011".U // CHECK

  def slt = "b100_0100".U // 需要比較
  def sltu = "b101_0100".U // 需要比較

  def or = "b000_1000".U
  def nor = "b000_1001".U
  def xor = "b000_1010".U
  def and = "b000_1011".U

  /** 判斷是否是減法. 設計用於傳遞給加法器進行減法
    *
    * @param ctrl
    * @return
    */
  def isSub(ctrl: UInt) = ctrl(6)

  /** 用於判斷是否是 Word 類型. 在32位的情況下沒有意義
    *
    * @param ctrl
    * @return
    */
  def isWordOp(ctrl: UInt) = ctrl(5)

  /** 判斷是否是無符號數
    *
    * @param ctrl
    * @return
    */
  def isUnsign(ctrl: UInt) = ctrl(4)

  /** 訪問編碼位
    *
    * @param ctrl
    * @return
    */
  def getEncoded(ctrl: UInt) = ctrl(3, 0)
}

class ALUIO extends FuCtrlIO {}
