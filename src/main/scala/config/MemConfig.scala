package config

import chisel3._
import chisel3.util._

case class MemConfig(
    // 用来跳过编译, 这样就可以在仿真中加速
    HasICache: Boolean = false,
    HasDCache: Boolean = false,
    // Level 2 Cache
    HasL2Cache: Boolean = false
) {}
