package config

import chisel3._
import chisel3.util._

// 定义互斥的基类
sealed trait BaseISAType
case object RV32E extends BaseISAType
case object RV32I extends BaseISAType
case object RV64I extends BaseISAType
case object RV128I extends BaseISAType

case class ISAConfig(
    baseType: BaseISAType, // 互斥, RV32E, RV32I, RV64I, RV128I

    // Extensions 这里是独立开关
    extM: Boolean = false, // 乘除法
    extA: Boolean = false, // 原子指令
    extC: Boolean = false, // 压缩指令
    extF: Boolean = false, // 单精度浮点
    extD: Boolean = false, // 双精度浮点
    extZicsr: Boolean = false
) {
  // 逻辑推导

  def numGPR: Int = baseType match {
    case RV32E  => 16
    case RV32I  => 32
    case RV64I  => 32
    case RV128I => 32
  }

  def xlen: Int = baseType match {
    case RV32E  => 32
    case RV32I  => 32
    case RV64I  => 64
    case RV128I => 64
  }

  // D 扩展依赖 F 扩展
  if (extD) require(extF, "Extension D requires Extension F")

  // TODO: 拓展依赖判断
}
