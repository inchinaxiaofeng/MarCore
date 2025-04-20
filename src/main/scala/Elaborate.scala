import circt.stage._
import module._
import units._
import top._
import top.chiplab._
import top.sta._
import java.nio.file.{Files, Path}
import org.json4s.native.Json

object Elaborate extends App {
  // 读取配置文件
//  val configJson = Files.readString(Path.of("config.json"))

//  def top = if (Settings.get("FPGAPlatform")) {
//    new SoC_LoongLabBox()
//  } else if (Settings.get("IsSimCore")) {
//    new SimTop()
//  } else if (Settings.get("STA_Core")) {
//    new STA_Core() // FIXME: 如何处理这里的问题
//  } else {
//    new SimTop()
//  }
//  def top = Settings.getT("ElabMode").asInstanceOf[ElabMode].module
  def top = new SimTop()
  val useMFC = true // use MLIR-based firrtl compiler
  val generator = Seq(chisel3.stage.ChiselGeneratorAnnotation(() => top))
  val firtoolOptions = Seq(
    FirtoolOption(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=wrapInAtSquareBracket"
    ),
    FirtoolOption("--split-verilog"),
    FirtoolOption("-o=build/sv-gen")
  )
  val chiselStageOptions =
    generator :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog)
  val executeOptions = firtoolOptions ++ chiselStageOptions
  if (useMFC) {
    (new ChiselStage).execute(args, executeOptions)
  } else {
    (new ChiselStage).execute(args, generator)
  }
}

// object Elaborate extends App {
//   def top = new TopMain()
//   (new ChiselStage).execute(args, Seq(
//     chisel3.stage.ChiselGeneratorAnnotation(() => top))
//   )
// }
