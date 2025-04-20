package settings

import scala.annotation.meta.field

/** 根據 Defs、Config，以及自行確定的配置模塊
  */
object DefaultSettings {
  def apply() = Map(
    "ResetVector" -> 0x80000000L,
    "MMIOBase" -> 0x00000000a0000000L,
    "MMIOSize" -> 0x0000000010000000L,
    //
    // "HasL2Cache" -> false, // 是否開啓 L2 Cache
    "HasPrefetch" -> false, // 目前只支持False
    "EnableMultiIssue" -> false, // 目前只支持False
    "EnableOutOfOrderExec" -> false, // 目前只支持False,
    // "HasITLB" -> false, // 是否開啓 ITLB, // 目前只支持False
    // "HasDTLB" -> false, // 是否開啓 DTLB, // 目前只支持False
    // "HasICache" -> true, // 是否開啓 ICache
    // "HasDCache" -> true, // 是否開啓 DCache

    "ICacheSwapPolicy" -> "LRU",
    "DCacheSwapPolicy" -> "LRU",

    // "IsSimCore" -> true, 遷移到Config中
    // "IsSimSoC" -> false, // TODO
    // "IsSTACore" -> false, 遷移到Config中
    // "FPGAPlatform" -> false, 這個太廣了，暫不支持
    // "EnableDebug" -> true,
    // "EnableDisplay" -> false, // 在终端打印调试信息，移動到 MarCoreConfig中。
    "EnableTrace" -> true,
    "EnableRVC" -> false,

    /* Log Settings */
    // "IsChiselRuntimeLog" -> true,
    // "IsChiselLogColorful" -> true,

    /* Impl Settings
     * NOTE:
     * All Impl Settings are suggested as "True".
     * "True" will impl module that has
     * good performance at FPGA with lowest gate level.
     * "False" will impl module that has
     * good performance at SIM cuz its simple enough.
     */
    "ImplBetterAdder" -> true,
    "ImplBetterMultiplier" -> true,
    "ImplBetterLogic" -> false, // replace AND & OR as NAND & NOR in module.fu.basis

    /* Simualtion Settings */
    // If not set true when Elaborating, firtool will fail and exit with code 134
    // BasicInfo
    "TraceBasicInfo" -> true,
    // Trace Func Unit
    "TraceLSU" -> false,
    "TraceALU" -> false,
    "TraceMDU" -> false,
    // Trace Pipeline Level
    "TraceEXU" -> false,
    // Trace Top IO
    "TraceLoadStore" -> false,
    "TraceMMIO" -> false,
    // Trace Cache
    "TraceCache" -> true,
    // Tmp, should remove as quickly.
    "TmpSet" -> true, // NOTE: not use
    // Difftest
    "EnableDifftest" -> true, // 启动Difftest
    "DiffTestGPR" -> true, // 对GPR进行Difftest
    "DiffTestCSR" -> true, // 对CSR进行Difftest
    // Statistics
    "Statistic" -> true, // 引入统计模块，进行性能分析
    // DebugBus
    "EnableDebugBus" -> true // 开启调试总线

    /* FPGA Settings */
  )
}

object Settings {
//var settings: Map[String, AnyVal] = DefaultSettings()
  var settings: Map[String, Any] = DefaultSettings()
  def get(field: String) = {
    settings(field).asInstanceOf[Boolean]
  }
  def getLong(field: String) = {
    settings(field).asInstanceOf[Long]
  }
  def getInt(field: String) = {
    settings(field).asInstanceOf[Int]
  }
  def getString(field: String) = {
    settings(field).asInstanceOf[String]
  }
  def getT[T](field: String): T = {
    settings(field).asInstanceOf[T]
  }
}
