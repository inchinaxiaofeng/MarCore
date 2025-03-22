package utils

import chisel3._
import chisel3.util._

import defs._

/** `PipelineConnect` 对象用于连接流水线中的两个 DecoupledIO 接口。 它处理流水线中的有效信号和数据传输，并支持流水线冲刷。
  *
  * @example
  *   {{{
  *     import chisel3._
  *     import chisel3.util._
  *
  *     class ExamplePipelineConnect extends Module {
  *       val io = IO(new Bundle {
  *         val in = Flipped(DecoupledIO(UInt(8.W)))
  *         val out = DecoupledIO(UInt(8.W))
  *         val outFire = Input(Bool())
  *         val flush = Input(Bool())
  *       })
  *
  *       PipelineConnect(io.in, io.out, io.outFire, io.flush)
  *     }
  *   }}}
  *
  * // 用法示例： // `PipelineConnect` 将 `io.in` 连接到 `io.out`，并处理流水线中的有效信号。 //
  * `io.outFire` 用于指示流水线右端何时交火，从而重置有效信号。 // `io.flush` 用于冲刷流水线，使输出无效。
  */
object PipelineConnect {

  /** 连接流水线中的两个 DecoupledIO 接口。
    *
    * @param left
    *   左侧的 DecoupledIO 接口。
    * @param right
    *   右侧的 DecoupledIO 接口。
    * @param rightOutFire
    *   指示流水线右端何时交火的信号。
    * @param isFlush
    *   指示是否冲刷流水线的信号。
    * @tparam T
    *   DecoupledIO 接口的数据类型。
    */
  def apply[T <: Data](
      left: DecoupledIO[T],
      right: DecoupledIO[T],
      rightOutFire: Bool,
      isFlush: Bool
  ) = {
    val valid = RegInit(false.B)
    when(rightOutFire) { valid := false.B } // 当流水线右端已经交火，则当前指令一定已经无效
    when(left.valid && right.ready) { valid := true.B }
    when(isFlush) { valid := false.B }

    left.ready := right.ready
    right.bits := RegEnable(left.bits, left.valid && right.ready)
    right.valid := valid // && !isFlush
  }
}
