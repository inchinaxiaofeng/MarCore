package config

import chisel3._
import chisel3.util._

// 与遥测相关的内容

case class DiffConfig(
    diffRegFile: Boolean = false, // 寄存器对比
    diffSystem: Boolean = false, // 系统状态与异常对比
    diffMemory: Boolean = false // 访存验证对比
) {
  // 逻辑推导
  // 核心规则：只要有一个开启，核心信号就必须开启
  // 在 Chisel 中，核心信号通常是基准，只要启用 Difftest 就会存在
  def isEnabled: Boolean = diffRegFile || diffSystem || diffMemory
}
