package defs_dev

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config._
import settings._

/** MarCore通用參數
  */
trait HasMarCoreParameter {
  // Based on config
  val XLEN = ISAConfig.getT("XLen") match {
    case XLen._32 => 32
    case XLen._64 => 64
  }
  val DataBits = XLEN
  val DataBytes = DataBits / 8
  val HasICache = BaseConfig.get("HasICache")
  val HasDCache = BaseConfig.get("HasDCache")

  // Based on settings
  val VAddrBits =
    64 // if (Settings.get("IsRV32")) 32 else 39 // VAddrBits is Virtual Memory addr bits
  val PAddrBits = 32 // PAddrBits is Physical Memory address bits
  val AddrBits = 64
  val EnableMultiIssue = Settings.get("EnableMultiIssue")
  val EnableOutOfOrderExec = Settings.get("EnableOutOfOrderExec")
}

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
  *   啓用增強Log之後，將會向代碼中添加格式化的輸出內容。
  */
case class MarCoreConfig(
    FPGAPlatform: Boolean = true,
    EnableDebug: Boolean = BaseConfig.getT("TopType") match {
      case TopType.ChipLab  => false
      case TopType.YosysSTA => false
      case TopType.SimCore  => true
      case TopType.SimSoC   => false
    },
    EnhancedLog: Boolean = true
)

/** 當處於 Chisel Test 中時，可以開啓選項，這樣會有色彩化輸出。
  *
  * 但是當在Verilog中運行時，可能會導致報錯。
  */
trait HasColorfulLog {
  val enable = BaseConfig.get("ColorfulLog")
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
