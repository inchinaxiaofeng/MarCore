package config

import scala.annotation.meta.field

private[config] object RISCVConfig {
  def apply() = Map(
    "XLen" -> XLen._64, // 機器字長,當前只支持64位。

    "ResetVector" -> 0x80000000L,
    "MMIOBase" -> 0x00000000a0000000L,
    "MMIOSize" -> 0x0000000010000000L
  )
}
