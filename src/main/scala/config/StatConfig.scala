package config

import chisel3._
import chisel3.util._

case class StatConfig(
    // Cache
    StatCache: Boolean = false,

    // Frontend
    StatIFO: Boolean = false,
    StatIDU: Boolean = false,
    StatISU: Boolean = false,
    StatEXU: Boolean = false,
    StatWBU: Boolean = false,

    // Fu
    StatBPU: Boolean = false,
    StatALU: Boolean = false,
    StatBRU: Boolean = false,
    StatMulU: Boolean = false,
    StatDivU: Boolean = false,
    StatLSU: Boolean = false
) {}
