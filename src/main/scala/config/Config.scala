package config

import scala.annotation.meta.field

/** 用於選擇架構寬度。
  *
  * 這個機器字長雖然是架構無關內容，理應在Config中指定。
  *
  * 但是考慮到目前各個架構基本上都只支持一個，所以將這個選項的指定下放到各個ISAConfig中。
  */
object XLen extends Enumeration {
  val _32, _64 = Value
}

object CacheReplacePolicy extends Enumeration {
  val NONE, LRU, LFU, FIFO, RAND = Value
}

/** 架構選擇枚舉
  */
object ISA extends Enumeration {
  val RISCV, MIPS, LoongArch = Value
}

/** 頂層模塊封裝對象枚舉
  *
  * * ChipLab: 面向ChipLab進行頂層封裝。
  *
  * * YosysSTA: 面向 Yosys-sta 工具的頂層封裝
  *
  * * SimCore：SimCore爲自行開發的頂層封裝，用於芯片層功能模擬，運行於 Verilator 環境中。
  *
  * * SimSoC：SimSoC爲自行開發的頂層封裝，用於SoC層功能模擬，運行與 Verilator 環境中。
  *
  * 注意，SimCore, SimSoC具體使用要求請參考倉庫主理人的ysyx項目
  */
object TopType extends Enumeration {
  val ChipLab, YosysSTA, SimCore, SimSoC = Value
}

/** 這個是一個全局的配置表，用於控制項目功能啓動、代碼的選擇編譯等等。
  *
  * 在這個配置表中，不應該出現任何 RTL 代碼，僅僅應該用於指定各種封裝好的選項
  *
  * 爲確保封裝可控，我們強制要求任何出現在 `Config` 中的選項都應該是 `Enum`
  */
private[config] object Config {
  def apply() = Map(
    // ==== Basic ====
    "ISA" -> ISA.LoongArch, // 架構
    "TopType" -> TopType.ChipLab, // 頂層封裝對象
    // ==== Struct ====
    "HasICache" -> false, // 目前只支持false
    "HasDCache" -> false, // 目前只支持false
    "HasL2Cache" -> false, // 目前只支持false
    "CacheReplacePolicy" -> CacheReplacePolicy.RAND,
    // ==== Log ====
    // === Cache ===
    "LogCache" -> false,
    // === Frontend ====
    "LogBPU" -> false,
    "LogIFU" -> false,
    "LogIDU" -> false,
    "LogISU" -> false,
    // === Backend ===
    "LogEXU" -> false,
    "LogWBU" -> false,
    // == Func Unit ==
    "LogALU" -> false,
    "LogBRU" -> false,
    "LogMulU" -> false,
    "LogDivU" -> false,
    "LogLSU" -> false
  )
}

/** 這個是用於引入基礎配置的
  */
object BaseConfig {
  var config: Map[String, Any] = Config()
  def get(field: String) = {
    config(field).asInstanceOf[Boolean]
  }
  @deprecated("Not Support in BaseConfig")
  def getLong(field: String) = {
    config(field).asInstanceOf[Long]
  }
  @deprecated("Not Support in BaseConfig")
  def getInt(field: String) = {
    config(field).asInstanceOf[Int]
  }
  @deprecated("Not Support in BaseConfig")
  def getString(field: String) = {
    config(field).asInstanceOf[String]
  }
  private def getT[T](field: String): T = {
    config(field).asInstanceOf[T]
  }

  /** MarCore Current ISA choices
    * @note
    *   ISA.LoongArch ISA.MIPS ISA.RISCV
    */
  val isa = getT[ISA.Value]("ISA")

  /** MarCore Current Top Module choices
    * @note
    *   ChipLab YosysSTA SimCore SimSoC
    */
  val top = getT[TopType.Value]("TopType")

  /** Cache 替换策略
    */
  val cache = getT[CacheReplacePolicy.Value]("CacheReplacePolicy")
}

/** 這個是用於引入 ISA 相關配置的
  */
object ISAConfig {
  val isaConfig: Map[String, Any] =
    BaseConfig.isa match {
      case ISA.RISCV     => RISCVConfig()
      case ISA.MIPS      => MIPSConfig()
      case ISA.LoongArch => LoongArchConfig()
    }
  def get(field: String) = {
    isaConfig(field).asInstanceOf[Boolean]
  }
  def getLong(field: String) = {
    isaConfig(field).asInstanceOf[Long]
  }
  def getInt(field: String) = {
    isaConfig(field).asInstanceOf[Int]
  }
  def getString(field: String) = {
    isaConfig(field).asInstanceOf[String]
  }
  def getT[T](field: String): T = {
    isaConfig(field).asInstanceOf[T]
  }
}
