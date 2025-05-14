package defs

import chisel3._
import chisel3.IO
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import config._
import settings._
import utils.HasColor
import module.fu.HasExceptionNO

/** MarCore通用參數
  */
trait HasMarCoreParameter {
  private val xlen = ISAConfig.getT[XLen.Value]("XLen")

  /** 機器字長 */
  val XLEN = xlen match {
    case XLen._32 => 32
    case XLen._64 => 64
  }
  val AddrBits = XLEN // 芯片内使用
  val DataBits = XLEN
  val DataBytes = DataBits / 8

  val VAddrBits = XLEN // Based on 分页
  val PAddrBits = 32 // PAddrBits is Physical Memory address bits

  val HasICache = BaseConfig.get("HasICache")
  val HasDCache = BaseConfig.get("HasDCache")

  val EnableMultiIssue = Settings.get("EnableMultiIssue")
  val EnableOutOfOrderExec = Settings.get("EnableOutOfOrderExec")
}

/** 全局使用的配置選項。
  *
  * 當實例化需要提供配置選項的模塊的時候，需要手動生成一個配置表，傳遞需要實例化的模塊。
  *
  * 因此，我們要求這個配置選項在使用與設計的時候，應該滿足這樣的想象：
  *   - 提供足夠簡單，但廣泛的配置選項。
  *   - 由於需要分佈式、實例化時進行配置，不應該被廣泛的使用。
  *   - 基於以上兩點，我們建議在非常頂層的位置去使用。
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
    EnableDebug: Boolean = BaseConfig.top match {
      case TopType.ChipLab  => false
      case TopType.YosysSTA => false
      case TopType.SimCore  => true
      case TopType.SimSoC   => false
    },
    EnhancedLog: Boolean = true
)

abstract class MarCoreModule
    extends Module
    with HasMarCoreParameter
    with HasMarCoreConst
    with HasExceptionNO
    with HasBackendConst
    with HasColor
abstract class MarCoreBundle
    extends Bundle
    with HasMarCoreParameter
    with HasMarCoreConst
    with HasExceptionNO
    with HasBackendConst
    with HasColor
    with __HasFU
