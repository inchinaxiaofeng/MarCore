package utils

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import utils.LogLevel.LogLevel

trait HasColor {
  var enableColor = false
  val ESC = "0x1b" // ESC = "\u001b" = 0x1b
  val prompt = ">>> "
  val blackFG = if (enableColor) s"${ESC}[30m" else ""
  val redFG = if (enableColor) s"${ESC}[31m" else ""
  val greenFG = if (enableColor) s"${ESC}[32m" else ""
  val yellowFG = if (enableColor) s"${ESC}[33m" else ""
  val blueFG = if (enableColor) s"${ESC}[34m" else ""
  val magentaFG = if (enableColor) s"${ESC}[35m" else ""
  val cyanFG = if (enableColor) s"${ESC}[36m" else ""
  val whiteFG = if (enableColor) s"${ESC}[37m" else ""

  val blackBG = if (enableColor) s"${ESC}[40m" else ""
  val redBG = if (enableColor) s"${ESC}[41m" else ""
  val greenBG = if (enableColor) s"${ESC}[42m" else ""
  val yellowBG = if (enableColor) s"${ESC}[43m" else ""
  val blueBG = if (enableColor) s"${ESC}[44m" else ""
  val magentaBG = if (enableColor) s"${ESC}[45m" else ""
  val cyanBG = if (enableColor) s"${ESC}[46m" else ""
  val whiteBG = if (enableColor) s"${ESC}[47m" else ""

  val resetColor = if (enableColor) s"${ESC}[0m" else "" // reset all set
  val bold = if (enableColor) s"${ESC}[1m" else ""
  val italic = if (enableColor) s"${ESC}[3m" else ""
  val underline = if (enableColor) s"${ESC}[4m" else ""
  val blink = if (enableColor) s"${ESC}[5m" else ""
  val reverse = if (enableColor) s"${ESC}[7m" else ""
}

object LogLevel extends Enumeration {
  type LogLevel = Value

  val ALL = Value(0, "ALL")
  val TRACE = Value("TRACE")
  val DEBUG = Value("DEBUG")
  val INFO = Value("INFO")
  val WARN = Value("WARN")
  val ERROR = Value("ERROR")
  val OFF = Value("OFF")
}

/** 通用的Log工具.
  *
  * @note
  *   使用LogUtil 需要注意:
  *   - 需要隱藏模塊名稱. 請通過 `implicit val moduleName: String = this.name` 進行指定.
  *   - 通過 `LogUtil.currentLogLevel = LogLevel.TRACE` 來指定不同的等級.
  *   - 通過 `LogUtil.display = false` 來控制模塊開關.
  *     - 需要注意的是, 在一些EDA工具中, 不支持在Verilog中使用Display語句.
  *     - 將display設置爲False, 將不會生成Display語句在Verilog中.
  *   - 通過 `LogUtil.enableColor = true` 來控制色彩化輸出.
  *     - 需要注意, 在一些EDA工具中, 不支持在Verilog中使用ANSI控制符.
  *     - 將enableColor設置爲false, 將不會添加控制符.
  *   - 在任何情況下, 都不應該修改LogUtils的靜默選項.
  *   - FIXME : 目前尚不能在模塊內去指定. 可能是因爲要直接轉化爲Chisel中的類型,
  *     enableColor不能直接指定(Var轉不到Chisel Type, 只能被認爲是Val而不可變. 但是目前好像還好.)
  */
object LogUtil extends HasColor {
  private var currentLogLevel: LogLevel = LogLevel.OFF

  def setLogLevel(level: LogLevel) = {
    currentLogLevel = level
  }

  /** 用於指定當前LogUtils是否啓動 */
  private var display = false

  def setDisplay(set: Boolean) = {
    display = set
  }

  def getDisplay: Boolean = {
    display
  }

  /** 默認的Log色彩
    */
  val colorMap = Map(
    LogLevel.TRACE -> cyanFG,
    LogLevel.DEBUG -> blueFG,
    LogLevel.INFO -> greenFG,
    LogLevel.WARN -> yellowFG,
    LogLevel.ERROR -> redFG
  )

  def apply(
      debugLevel: LogLevel
  )(prefix: Boolean, cond: Bool, pable: Printable)(implicit
      name: String
  ): Any = {
    val commonInfo = p"[${GTimer()}] $name: "
    val colorCode = colorMap.getOrElse(debugLevel, "")

    val shouldPrint = debugLevel match {
      case LogLevel.TRACE => currentLogLevel <= LogLevel.TRACE
      case LogLevel.DEBUG => currentLogLevel <= LogLevel.DEBUG
      case LogLevel.INFO  => currentLogLevel <= LogLevel.INFO
      case LogLevel.WARN  => currentLogLevel <= LogLevel.WARN
      case LogLevel.ERROR => currentLogLevel <= LogLevel.ERROR
    }
    when(cond && getDisplay.B && shouldPrint.B) {
      if (prefix) printf(commonInfo)
      printf(p"${colorCode}")
      printf(pable)
      printf(p"${resetColor}")
    }
  }
}

sealed abstract class LogHelper(val logLevel: LogLevel) {
  def apply(cond: Bool, fmt: String, data: Bits*)(implicit name: String): Any =
    apply(cond, Printable.pack(fmt, data: _*))

  def apply(cond: Bool, pable: Printable)(implicit name: String): Any =
    apply(true, cond, pable)

  def apply(fmt: String, data: Bits*)(implicit name: String): Any =
    apply(true.B, Printable.pack(fmt, data: _*))

  def apply(pable: Printable)(implicit name: String): Any = apply(true.B, pable)

  def apply(prefix: Boolean, fmt: String, data: Bits*)(implicit
      name: String
  ): Any = apply(prefix, true.B, Printable.pack(fmt, data: _*))

  def apply(prefix: Boolean, pable: Printable)(implicit name: String): Any =
    apply(prefix, true.B, pable)

  def apply(prefix: Boolean, cond: Bool, fmt: String, data: Bits*)(implicit
      name: String
  ): Any =
    apply(prefix, cond, Printable.pack(fmt, data: _*))

  def apply(prefix: Boolean, cond: Bool, pable: Printable)(implicit
      name: String
  ): Any =
    LogUtil(logLevel)(prefix, cond, pable)

  def apply(flag: Boolean = true, cond: Bool = true.B)(
      body: => Unit
  ): Any = {
    if (flag) { when(cond && LogUtil.getDisplay.B) { body } }
  }
}

object Trace extends LogHelper(LogLevel.TRACE)
object Debug extends LogHelper(LogLevel.DEBUG)
object Info extends LogHelper(LogLevel.INFO)
object Warn extends LogHelper(LogLevel.WARN)
object Error extends LogHelper(LogLevel.ERROR)

object ShowType {
  def apply[T: Manifest](t: T) = println(manifest[T])
}
