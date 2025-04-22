package utils

import chisel3._
import chisel3.util._

/** 全局計時器, 常常用於標誌輸出內容
  */
object GTimer {
  def apply() = {
    val c = RegInit(0.U(64.W))
    c := c + 1.U
    c
  }
}

