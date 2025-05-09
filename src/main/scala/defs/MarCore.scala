package defs

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import top.Settings
import module.fu._
//import module.ooo.HasBackendConst

trait HasColorfulLog {
  val enable = Settings.get("IsChiselLogColorful")
  val prompt = ">>> "
  val blackFG = if (enable) "\u001b[30m" else ""
  val redFG = if (enable) "\u001b[31m" else ""
  val greenFG = if (enable) "\u001b[32m" else ""
  val yellowFG = if (enable) "\u001b[33m" else ""
  val blueFG = if (enable) "\u001b[34m" else ""
  val magentaFG = if (enable) "\u001b[35m" else ""
  val cyanFG = if (enable) "\u001b[36m" else ""
  val whiteFG = if (enable) "\u001b[37m" else ""

  val blackBG = if (enable) "\u001b[40m" else ""
  val redBG = if (enable) "\u001b[41m" else ""
  val greenBG = if (enable) "\u001b[42m" else ""
  val yellowBG = if (enable) "\u001b[43m" else ""
  val blueBG = if (enable) "\u001b[44m" else ""
  val magentaBG = if (enable) "\u001b[45m" else ""
  val cyanBG = if (enable) "\u001b[46m" else ""
  val whiteBG = if (enable) "\u001b[47m" else ""

  val resetColor = if (enable) "\u001b[0m" else "" // reset all set
  val bold = if (enable) "\u001b[1m" else ""
  val italic = if (enable) "\u001b[3m" else ""
  val underline = if (enable) "\u001b[4m" else ""
  val blink = if (enable) "\u001b[5m" else ""
  val reverse = if (enable) "\u001b[7m" else ""
}

trait HasMarCoreParameter {
  // General parameter for MarCore
  val ZEROREG = 0
  val NR_GPR = 32
  val NR_CSR = 4096
  val RegIDWidth = 5
  val CSRIDWidth = 12
  val BYTELEN = 8
//**********************************************
  val XLEN = 64 // if (Settings.get("IsRV32")) 32 else 64
  val HasICache = Settings.get("HasICache")
  val HasDCache = Settings.get("HasDCache")
  val HasMExtension = true
  val HasCExtension = Settings.get("EnableRVC")
  val HasDiv = true
  val VAddrBits =
    64 // if (Settings.get("IsRV32")) 32 else 39 // VAddrBits is Virtual Memory addr bits
  val PAddrBits = 32 // PAddrBits is Physical Memory address bits
  val AddrBits = 64
  val DataBits = XLEN
  val DataBytes = DataBits / 8
  val EnableMultiIssue = Settings.get("EnableMultiIssue")
  val EnableOutOfOrderExec = Settings.get("EnableOutOfOrderExec")
}

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

abstract class MarCoreModule
    extends Module
    with HasMarCoreParameter
    with HasMarCoreConst
    with HasExceptionNO
    with HasRISCInstrParameter
    with HasBackendConst
    with HasColorfulLog
abstract class MarCoreBundle
    extends Bundle
    with HasMarCoreParameter
    with HasMarCoreConst
    with HasExceptionNO
    with HasRISCInstrParameter
    with HasBackendConst
    with HasColorfulLog
    with __HasFU

/** 全局使用的配置選項。
  *
  * 當實例化需要提供配置選項的模塊的時候，需要手動生成一個配置表，傳遞需要實例化的模塊。
  *
  * 因此，我們要求這個配置選項在使用與設計的時候，應該滿足這樣的想象：
  *
  *   1. 提供足夠簡單，但廣泛的配置選項。
  *
  * 2. 由於需要分佈式、實例化時進行配置，不應該被廣泛的使用。
  *
  * 3. 基於以上兩點，我們建議在非常頂層的位置去使用。
  *
  * @param FPGAPlatform
  *   啓用 FPGA 支持，啓動後提供對 `FPGA` 板的支持。
  * @param EnableDebug
  *   啓用 Debug 總線。Debug總線被設計爲一條用於在功能驗證的情況下，通過飛線對特定模塊進行控制、查詢、交互、修改的總線。
  *   理論上沒有一個FPGA平臺有足夠的接口數量以容納Debug總線，因此這個選項往往用於在定製化的仿真環境中調試時開啓。
  * @param EnhancedLog
  *   啓用增強Log之後，將會向代碼中添加色彩化、格式化的輸出內容。但是這些內容目前僅僅支持在 ChiselTest 中輸出。
  *   FIXME:在遷移代碼後，這個功能已經實質性失效。
  */
case class MarCoreConfig(
    FPGAPlatform: Boolean = true,
    EnableDebug: Boolean = Settings.get("EnableDebug"),
    EnhancedLog: Boolean = true
)

object AddressSpace extends HasMarCoreParameter {
  // (start, size)
  // address out of MMIO will be considered as DRAM
  def mmio = List(
    (0x30000000L, 0x10000000L), // internal devices, such as CLINT and PLIC
    (
      Settings.getLong("MMIOBase"),
      Settings.getLong("MMIOSize")
    ) // external devices
  )

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

trait HasRISCInstrParameter {
  val InstrBits = 32

  val OpcodeBits = 7

  val DoubleBits = 64
  val WordBits = 32
  val HalfBits = 16
  val ByteBits = 8

  val CSRHi = 31
  val CSRLo = 20

  val RS1Hi = 19
  val RS1Lo = 15
  val RS2Hi = 24
  val RS2Lo = 20
  val RDHi = 11
  val RDLo = 7
}

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

// object ALUCtrl {
// 	def WIDTH = 6
//
// 	def ADD     = "b000000".U
// 	def SUB     = "b000001".U
// 	def SLL     = "b000010".U
// 	def SLT     = "b000011".U
// 	def SLTU    = "b000100".U
// 	def XOR     = "b000101".U
// 	def SRL     = "b000110".U
// 	def SRA     = "b000111".U
// 	def OR      = "b001000".U
// 	def AND     = "b001001".U
// 	def NAND	= "b100001".U
//
// 	def ADDW    = "b001010".U
// 	def SUBW    = "b001011".U
// 	def SLLW    = "b001100".U
// 	def SLTW    = "b001101".U
// 	def SLTUW   = "b001110".U
// 	def XORW    = "b001111".U
// 	def SRLW    = "b010000".U
// 	def SRAW    = "b010001".U
// 	def ORW     = "b010010".U
// 	def ANDW    = "b010011".U
//
// 	def MUL     = "b010100".U
// 	def MULH    = "b010101".U
// 	def MULHSU  = "b010110".U
// 	def MULHU   = "b010111".U
// 	def DIV     = "b011000".U
// 	def DIVU    = "b011001".U
// 	def REM     = "b011010".U
// 	def REMU    = "b011011".U
//
// 	def MULW    = "b011100".U
// 	def DIVW    = "b011101".U
// 	def DIVUW   = "b011110".U
// 	def REMW    = "b011111".U
// 	def REMUW   = "b100000".U
//
// 	def NOCARE = "b000000".U}

object MemRW {
  def WIDTH = 2

  def NONE = "b00".U
  def READ = "b01".U
  def WRITE = "b10".U

  def NOCARE = "b00".U
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

  def NOCARE = "b000".U
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

  def NOCARE = "b0000".U
}

object ALUSrcA {
  def WIDTH = 2

  def REG = "b00".U
  def PC = "b01".U
  def P0 = "b10".U
  def CSR = "b11".U

  def NOCARE = "b00".U
}

object ALUSrcB {
  def WIDTH = 3

  def REG = "b000".U
  def IMM = "b001".U
  def P0 = "b010".U
  def P4 = "b011".U
  def PB = "b100".U
  def CSR = "b101".U

  def NOCARE = "b000".U
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

  def NOCARE = "b0".U
}

object Branch {
  def F = "b0".U
  def T = "b1".U

  def NOCARE = "b0".U
}

object Jump {
  def WIDTH = 2

  def NONE = "b00".U
  def JUMP = "b01".U
  def MEPC = "b10".U

  def NOCARE = "b0".U
}

object PCPlusSrc {
  def WIDTH = 1
  def PC = "b0".U
  def REG = "b1".U

  def NOCARE = "b0".U
}

object NPCSrc {
  def WIDTH = 2

  def PCP4 = "b00".U
  def JUMP = "b01".U
  def MEPC = "b10".U
}
