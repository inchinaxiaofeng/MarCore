// See LICENSE.Berkeley for license details.

package utils

import chisel3._
import chisel3.util._

/** `LatencyPipe` 模块用于在 DecoupledIO 接口中引入指定的延迟。 它通过串联多个 `Queue` 模块来实现延迟，每个
  * `Queue` 模块的深度为 1。
  *
  * @param typ
  *   DecoupledIO 接口的数据类型 (T)。
  * @param latency
  *   要引入的延迟周期数。
  * @tparam T
  *   DecoupledIO 接口的数据类型。
  *
  * @example
  *   {{{
  *     import chisel3._
  *     import chisel3.util._
  *
  *     class ExampleLatencyPipe extends Module {
  *         val io = IO(new Bundle {
  *         val in = Flipped(DecoupledIO(UInt(8.W)))
  *         val out = DecoupledIO(UInt(8.W))
  *       })
  *
  *       io.out <> LatencyPipe(io.in, 3) // 引入 3 个周期的延迟
  *     }
  *   }}}
  *
  * // 用法示例： // 如果输入 `in` 在第 0 个周期有效，那么输出 `out` 将在第 3 个周期有效。 // 延迟期间，数据被存储在内部的
  * `Queue` 模块中。
  */
class LatencyPipe[T <: Data](typ: T, latency: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(DecoupledIO(typ))
    val out = DecoupledIO(typ)
  })

  /** 递归地应用函数 `func` `n` 次。
    *
    * @param n
    *   应用函数的次数。
    * @param func
    *   要应用的函数。
    * @param in
    *   输入值。
    * @tparam T
    *   输入和输出值的类型。
    * @return
    *   应用函数 `n` 次后的结果。
    */
  def doN[T](n: Int, func: T => T, in: T): T =
    (0 until n).foldLeft(in)((last, _) => func(last))

  io.out <> doN(
    latency,
    (last: DecoupledIO[T]) => Queue(last, 1, pipe = true),
    io.in
  )
}

/** `LatencyPipe` 对象提供了一个便捷的 `apply` 方法，用于创建 `LatencyPipe` 模块。
  */
object LatencyPipe {

  /** 创建一个 `LatencyPipe` 模块，并将输入连接到模块的输入端口。
    *
    * @param in
    *   输入 DecoupledIO 接口。
    * @param latency
    *   要引入的延迟周期数。
    * @tparam T
    *   DecoupledIO 接口的数据类型。
    * @return
    *   输出 DecoupledIO 接口。
    */
  def apply[T <: Data](in: DecoupledIO[T], latency: Int): DecoupledIO[T] = {
    val pipe = Module(new LatencyPipe(in.bits.cloneType, latency))
    pipe.io.in <> in
    pipe.io.out
  }
}

