package config

import scala.annotation.meta.field

private[config] object LoongArchConfig {
  def apply() = Map(
    "XLen" -> XLen._32, // 機器字長，當前只支持32位

    "ResetVector" -> 0x1c00_0000L,
    "ResetVectorSub4" -> 0x1bff_fffc,
    "MMIOBase" -> 0x0000_0000_0200_0000L,
    "MMIOSize" -> 0x0000_0000_2000_0000L
  )
}
