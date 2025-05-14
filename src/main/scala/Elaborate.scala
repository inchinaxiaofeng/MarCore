/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
import circt.stage._
import chisel3._
import chisel3.stage._
import java.nio.file.{Files, Path}
import module.fu.ALU

/** `mill MarCore.runMain Elaborate` to run this.
  */
object Elaborate extends App {
  // 1) 在这里列出所有要生成的（模块名，输出目录）对
  val jobs = Seq(
    ("ALU", "build/module/fu/ALU")
    // 如果还有其他 top，就继续加：
    // ("YourTop", "out/yourtop")
  )

  // 2) 根据名字返回一个 Generator 函数
  def makeGen(name: String): () => RawModule = name match {
    case "ALU" => () => new ALU()
    // case "YourTop"     => () => new YourTop()
    case other => throw new IllegalArgumentException(s"Unknown top: $other")
  }

  // 3) 公用的 CIRCT + firtool 配置
  val baseFirtoolOpts = Seq(
    /** 禁止在 FIRRTL 中產生類似 Verilog logic [x:0] foo; 這樣在 module 中只用於本地的變量
      *
      * 意圖是： 避免生成“module 局部的中間變數”，而是強制所有東西都展開成「連線（wire）+ 賦值」，這樣對部分 EDA
      * 工具更友善，也更容易跟踪訊號流。
      *
      * 影響： 你會發現所有變數會變成 assign foo = ...;，而不是 logic foo; 然後單獨賦值。
      */
    FirtoolOption("--lowering-options=disallowLocalVariables"),
    /** 禁止使用 SystemVerilog 的「打包陣列（packed array）」
      *
      * 範例：
      * {{{
      * logic [3:0][7:0] memory; // packed array，4 個 8-bit 的 word
      * }}}
      *
      * 為什麼要禁用？ 這種寫法雖然在 SystemVerilog 裡合法，但有些工具（特別是老版本或硬核合成工具）會對這種“陣列的陣列”支援不佳。
      * 這個選項會把它轉換為普通的 reg [7:0] memory[3:0]; 或展開成扁平化的結構。
      *
      * 結果： 產生的 Verilog 更像 RTL 設計師手寫的那種，也避免工具抱怨。
      */
    FirtoolOption("--lowering-options=disallowPackedArrays"),
    FirtoolOption("--split-verilog") // 拆分成多個.sv文件, 当要求进行拆分时, 会向目录输出.
    // FirtoolOption("--preserve-values=all"), // 保留中间变量名
    // FirtoolOption("--strip-debug-info=0") // 保留调试信息
    // FirtoolOption("--emit-omir"), // 生成JSON
    // FirtoolOption("--export-module-hierarchy") // 導出模塊實例樹
  )

  // 4) 对每一个 job 启动一次 ChiselStage
  jobs.foreach { case (topName, outDir) =>
    println(s"[ElaborateAll] Generating $topName → $outDir")

    // 4.1 生成 annotation 列表
    val annos = Seq(
      ChiselGeneratorAnnotation(makeGen(topName)),
      CIRCTTargetAnnotation(CIRCTTarget.Verilog)
    ) ++ baseFirtoolOpts ++ Seq(
      // 指定 firtool 输出目录
      FirtoolOption(s"-o=$outDir")
    )

    // 4.2 每次调用时，传入单独的 --target-dir，确保产物不会互相覆盖
    val stageArgs = Array(s"--target-dir=$outDir")

    // 4.3 执行
    (new ChiselStage).execute(stageArgs, annos)
  }
}
