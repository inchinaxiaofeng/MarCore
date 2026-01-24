package core.uarch.branch

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._

/** BTB 類型
  */
object BTBType {

  /** Branch 類型, 表現爲轉移方向不確定, 地址確定
    */
  def B = "b00".U

  /** Jump 類型, 表現爲轉移方向確定, 地址確定
    */
  def J = "b01".U

  /** Indirect 類型, 表現爲轉移方向確定, 地址不確定
    *
    * @return
    */
  def I = "b10".U

  /** CallRet 類型, 函數調用
    *
    * @return
    */
  def R = "b11".U // return

  def apply() = UInt(2.W)
}
