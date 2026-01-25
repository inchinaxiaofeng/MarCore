package config

import chisel3._
import chisel3.util._

case class LogConfig(
    // Pipeline
    LogFrontend: Boolean = false,
    LogBackend: Boolean = false,

    // Units
    LogIFU: Boolean = false,
    LogIDU: Boolean = false,
    LogISU: Boolean = false,
    LogEXU: Boolean = false,
    LogWBU: Boolean = false,

    // Fu
    LogBPU: Boolean = false,
    LogALU: Boolean = false,
    LogBRU: Boolean = false,
    LogMulU: Boolean = false,
    LogDivU: Boolean = false,
    LogLSU: Boolean = false,

    // Cache
    LogCache: Boolean = false
) {}
