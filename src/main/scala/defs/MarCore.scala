package defs

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import top.Settings
import module.fu._
import utils.HasColor
import config.BaseConfig
import config.ISA

trait HasBackendConst {
  val robSize = 16
  val robWidth = 2
  val robInstCapacity = robSize * robWidth
  val checkpointSize = 4 // register map checkpoint size
  val brTagWidth = log2Up(checkpointSize)
  val prfAddrWidth =
    log2Up(robSize) + log2Up(robWidth) // physical rf addr width

  val DispatchWidth = 2
  val CommitWidth = 2
  val RetireWidth = 2

  val enablCheckpoint = true
}

// NEW
trait HasMarCoreConst extends HasMarCoreParameter {
  val CacheReadWidth = 64
  val DCacheUserBundleWidth = 0
  val ICacheUserBundleWidth = VAddrBits * 2 // For PC and NPC
  val IndependentBru = if (Settings.get("EnableOutOfOrderExec")) true else false
}

object AddressSpace extends HasMarCoreParameter {
  def mmio = BaseConfig.isa match {
    case ISA.LoongArch => LoongArchDefs.mmio
    case ISA.MIPS      => MIPSDefs.mmio
    case ISA.RISCV     => RISCVDefs.mmio
  }

  def isMMIO(addr: UInt) = mmio
    .map(range => {
      require(isPow2(range._2))
      val bits = log2Up(range._2)
      (addr ^ range._1.U)(PAddrBits - 1, bits) === 0.U
    })
    .reduce(_ || _)
}

trait __HasFU {
  def FUOpTypeBits = 5
  def FUTypeBits = 2
}

trait HasCtrlParameter {}

object ForwardE {
  def WIDTH = 2

  def RDE = "b00".U
  def ALUM = "b01".U
  def RDW = "b10".U
}

object ForwardD {
  def WIDTH = 2

  def RDD = "b00".U
  def ALUM = "b01".U
  def RDW = "b10".U
}

object MemRW {
  def WIDTH = 2

  def NONE = "b00".U
  def READ = "b01".U
  def WRITE = "b10".U
}

object MemWidth {
  def WIDTH = 3

  def BYTE = "b000".U
  def HALF = "b001".U
  def WORD = "b010".U
  def DOUBLE = "b011".U
  def BYTEU = "b100".U
  def HALFU = "b101".U
  def WORDU = "b110".U
}

object ByteMask {
  def NONE = "b00000000".U
  def BYTE = "b00000001".U
  def HALF = "b00000011".U
  def WORD = "b00001111".U
  def DOUBLE = "b11111111".U
}

object BranchCtrl {
  def WIDTH = 4

  def BEQ = "b0000".U
  def BNE = "b0001".U
  def BLT = "b0100".U
  def BGE = "b0101".U
  def BLTU = "b0110".U
  def BGEU = "b0111".U
  def JAL = "b1000".U
}

object ALUSrcA {
  def WIDTH = 2

  def REG = "b00".U
  def PC = "b01".U
  def P0 = "b10".U
  def CSR = "b11".U
}

object ALUSrcB {
  def WIDTH = 3

  def REG = "b000".U
  def IMM = "b001".U
  def P0 = "b010".U
  def P4 = "b011".U
  def PB = "b100".U
  def CSR = "b101".U
}

object RegWrite {
  def F = "b0".U
  def T = "b1".U

  def NOCARE = "b0".U
}

object CSRWrite {
  def F = "b0".U
  def T = "b1".U

  def NOCARE = "b0".U
}

object MemtoReg {
  def F = "b0".U
  def T = "b1".U
}

object Branch {
  def F = "b0".U
  def T = "b1".U
}

object Jump {
  def WIDTH = 2

  def NONE = "b00".U
  def JUMP = "b01".U
  def MEPC = "b10".U
}

object PCPlusSrc {
  def WIDTH = 1
  def PC = "b0".U
  def REG = "b1".U
}

object NPCSrc {
  def WIDTH = 2

  def PCP4 = "b00".U
  def JUMP = "b01".U
  def MEPC = "b10".U
}
