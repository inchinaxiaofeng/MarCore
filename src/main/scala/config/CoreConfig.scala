package config

import chisel3._
import chisel3.util._

case class CoreConfig(
    EnableMultiIssue: Boolean = false,
    EnableOutOfOrderExec: Boolean = false
) {}
