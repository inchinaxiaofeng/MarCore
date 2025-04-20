package top

import scala.annotation.meta.field
import top._
import top.sta._

//case class ElabMode(name:String, module:chisel3.RawModule)
case class VGAMode(
    mode: String,
    hsync: Int,
    hback: Int,
    hleft: Int,
    hvalid: Int,
    hright: Int,
    hfront: Int,
    htotal: Int,
    vsync: Int,
    vback: Int,
    vtop: Int,
    vvalid: Int,
    vbottom: Int,
    vfront: Int,
    vtotal: Int
)
case class RGBMode(mode: String, r: Int, g: Int, b: Int)

//object ElabMode extends Enumeration {
//  val SimCore   = ElabMode("SimTop", new SimTop())
//  val STACore   = ElabMode("Core", new STA_Core())
//  val FPGALoong = ElabMode("SoC_LoongLabBox", new SoC_LoongLabBox())
//}

object VGAMode extends Enumeration {
  val _640x480at60 =
    VGAMode("640x480@60", 96, 40, 8, 640, 8, 8, 800, 2, 25, 8, 480, 8, 2, 525)
  val _640x480at75 =
    VGAMode("640x480@75", 64, 120, 0, 640, 0, 16, 840, 3, 16, 0, 480, 0, 1, 500)
  val _800x600at60 = VGAMode(
    "800x600@60",
    128,
    88,
    0,
    800,
    0,
    40,
    1056,
    4,
    23,
    0,
    600,
    0,
    1,
    628
  )
  val _800x600at75 = VGAMode(
    "800x600@75",
    80,
    160,
    0,
    800,
    0,
    16,
    1056,
    3,
    21,
    0,
    600,
    0,
    1,
    625
  )
  val _1025x768at60 = VGAMode(
    "1024x768@60",
    136,
    160,
    0,
    1024,
    0,
    24,
    1344,
    6,
    29,
    0,
    768,
    0,
    3,
    806
  )
  val _1024x768at75 = VGAMode(
    "1024x768@75",
    176,
    176,
    0,
    1024,
    0,
    16,
    1312,
    3,
    28,
    0,
    768,
    0,
    3,
    806
  )
  val _1280x1024at60 = VGAMode(
    "1280x1024@60",
    112,
    248,
    0,
    1280,
    0,
    48,
    1688,
    3,
    38,
    0,
    1024,
    0,
    1,
    1066
  )
}

object RGBMode extends Enumeration {
  val _444 = RGBMode("ARGB444", 4, 4, 4)
  val _888 = RGBMode("ARGB888", 8, 8, 8)
}

object DefaultSettings {
  def apply() = Map(
    "ResetVector" -> 0x80000000L,
    "MMIOBase" -> 0x00000000a0000000L,
    "MMIOSize" -> 0x0000000010000000L,
    "Arch" -> "RISCV", // RISC V, LoongArch
    "CPU_2CMT" -> false, // FOR MIPS

    "HasL2Cache" -> false,
    "HasPrefetch" -> false,
    "EnableMultiIssue" -> false,
    "EnableOutOfOrderExec" -> false,
    "HasITLB" -> false,
    "HasDTLB" -> false,
    "HasICache" -> true,
    "HasDCache" -> true,
    "ICacheSwapPolicy" -> "LRU",
    "DCacheSwapPolicy" -> "LRU",
    "IsRV32" -> false,

//    "ElabMode"                -> ElabMode.STACore,
    "IsSimCore" -> true,
    "IsSimSoC" -> false, // TODO
    "IsSTACore" -> false,
    "FPGAPlatform" -> false,
    "EnableDebug" -> true,
    "EnableDisplay" -> false, // 在终端打印调试信息
    "EnableTrace" -> true,
    "EnableRVC" -> false,

    /* Log Settings */
    "IsChiselRuntimeLog" -> true,
    "IsChiselLogColorful" -> true,

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
    "EnableDebugBus" -> true, // 开启调试总线

    /* FPGA Settings */
    // VGA
    "VGAMode" -> VGAMode._800x600at60,
    "RGBMode" -> RGBMode._444
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

trait General_ConstReq_RV64 {
  require(!Settings.get("IsRV32"))
  require(Settings.getString("Arch") == "RISCV")
}

trait Sim_ConstReq {
//	require(Settings.get("EnableDifftest"))
//	require(Settings.get("EnableDebugBus"))
//  require(Settings.get("IsSimCore"))
}

trait STACore_ConstReq {
///  require(Settings.get("IsSTACore"))
///  require(!Settings.get("IsSimCore"))
///  require(!Settings.get("IsSimSoC"))
///  require(!Settings.get("EnableDifftest"))
///  require(!Settings.get("EnableDebugBus"))
///  require(!Settings.get("Statistic"))
}

trait SoC_LoongLabBox_ConstReq {
  require(Settings.get("FPGAPlatform"))
  require(!Settings.get("EnableDifftest"))
  require(!Settings.get("EnableDebugBus"))
  require(!Settings.get("Statistic"))
  require(Settings.getT("VGAMode").asInstanceOf[VGAMode].mode == "800x600@60")
  require(Settings.getT("RGBMode").asInstanceOf[RGBMode].mode == "ARGB444")
}
