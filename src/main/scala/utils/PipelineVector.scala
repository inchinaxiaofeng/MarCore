package utils

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import utils._

/** `PipelineVector2Connect` 对象用于连接两个输入 DecoupledIO 接口 (`in1`, `in2`) 到两个输出
  * DecoupledIO 接口 (`out1`, `out2`)， 并使用环形缓冲区管理数据流。它支持流水线冲刷和缓冲区大小配置。
  *
  * @param gen
  *   DecoupledIO 接口的数据类型 (T)。
  * @param in1
  *   第一个输入 DecoupledIO 接口。
  * @param in2
  *   第二个输入 DecoupledIO 接口。
  * @param out1
  *   第一个输出 DecoupledIO 接口。
  * @param out2
  *   第二个输出 DecoupledIO 接口。
  * @param flush
  *   冲刷信号，用于清空环形缓冲区。
  * @param bufferSize
  *   环形缓冲区的大小。
  * @tparam T
  *   DecoupledIO 接口的数据类型。
  *
  * @example
  *   {{{
  *     import chisel3._
  *     import chisel3.util._
  *
  *     class ExamplePipelineVector2Connect(dataWidth: Int, bufferSize: Int) extends Module {
  *       val io = IO(new Bundle {
  *         val in1 = Flipped(DecoupledIO(UInt(dataWidth.W)))
  *         val in2 = Flipped(DecoupledIO(UInt(dataWidth.W)))
  *         val out1 = DecoupledIO(UInt(dataWidth.W))
  *         val out2 = DecoupledIO(UInt(dataWidth.W))
  *         val flush = Input(Bool())
  *       })
  *
  *       PipelineVector2Connect(UInt(dataWidth.W), io.in1, io.in2, io.out1, io.out2, io.flush, bufferSize)
  *     }
  *
  *     // 用法示例：
  *     // `PipelineVector2Connect` 将两个输入接口的数据存储在环形缓冲区中，并将其转发到两个输出接口。
  *     // `flush` 信号用于清空缓冲区，`bufferSize` 用于配置缓冲区的大小。
  *     // 缓冲区采用先进先出 (FIFO) 方式管理数据。
  *   }}}
  */
object PipelineVector2Connect {
  def apply[T <: Data](
      gen: T,
      in1: DecoupledIO[T],
      in2: DecoupledIO[T],
      out1: DecoupledIO[T],
      out2: DecoupledIO[T],
      flush: Bool,
      bufferSize: Int
  ) = {
    // ring buffer
    val dataBuffer = RegInit(VecInit(Seq.fill(bufferSize)(0.U.asTypeOf(gen))))
    val ringBufferHead = RegInit(0.U(log2Up(bufferSize).W))
    val ringBufferTail = RegInit(0.U(log2Up(bufferSize).W))
    val ringBufferEmpty = ringBufferHead === ringBufferTail
    val ringBufferAllowin = (0 to 1)
      .map(i => (ringBufferHead + (i + 1).U) =/= ringBufferTail)
      .foldRight(true.B)((sum, i) => sum & i)

    // enqueue
    val needEnqueue = Wire(Vec(2, Bool()))
    needEnqueue(0) := in1.valid
    needEnqueue(1) := in2.valid
    Debug() {
//			printf("needEnqueue (%d,%d)(%d,%d)\n", needEnqueue(0), in1.ready, needEnqueue(1), in2.ready)
//			printf("HEAD? pc0x%x instr0x%x; pc0x%x instr0x%x; pc0x%x instr0x%x ?END\n",
//				dataBuffer(ringBufferHead).cf.pc, dataBuffer(ringBufferHead).cf.instr,
//				dataBuffer(ringBufferHead+1.U).cf.pc, dataBuffer(ringBufferHead+1.U).cf.instr,
//				dataBuffer(ringBufferHead+2.U).cf.pc, dataBuffer(ringBufferHead+2.U).cf.instr)
    }

    val enqueueSize =
      needEnqueue(0).asUInt +& needEnqueue(
        1
      ).asUInt // count(true) in needEnqueue
    val enqueueFire = (0 to 1).map(i => enqueueSize >= (i + 1).U)

    val wen = in1.fire || in2.fire // i.e. ringBufferAllowin && in.valid
    when(wen) {
      when(enqueueFire(0)) {
        dataBuffer(0.U + ringBufferHead) := Mux(
          needEnqueue(0),
          in1.bits,
          in2.bits
        )
      }
      when(enqueueFire(1)) { dataBuffer(1.U + ringBufferHead) := in2.bits }
      ringBufferHead := ringBufferHead + enqueueSize
    }

    in1.ready := ringBufferAllowin || !in1.valid
    in2.ready := ringBufferAllowin || !in2.valid

    // dequeue socket 1
    val deq1_StartIndex = ringBufferTail
    out1.bits := dataBuffer(deq1_StartIndex)
    out1.valid := ringBufferHead =/= deq1_StartIndex

    // dequeue socket 2
    val deq2_StartIndex = ringBufferTail + 1.U
    out2.bits := dataBuffer(deq2_StartIndex)
    out2.valid := ringBufferHead =/= deq2_StartIndex && out1.valid

    // dequeue control
    val dequeueSize = out1.fire.asUInt +& out2.fire.asUInt
    val dequeueFire = dequeueSize > 0.U
    when(dequeueFire) {
      ringBufferTail := ringBufferTail + dequeueSize;
    }

    // flush control
    when(flush) {
      ringBufferHead := 0.U
      ringBufferTail := 0.U
    }

    Debug() {
      printf(
        "[DPQ] size %x head %x tail %x enq %x deq %x\n",
        (bufferSize.asUInt +& ringBufferHead.asUInt - ringBufferTail.asUInt) % bufferSize.asUInt,
        ringBufferHead,
        ringBufferTail,
        enqueueSize,
        dequeueSize
      )
    }
  }
}
