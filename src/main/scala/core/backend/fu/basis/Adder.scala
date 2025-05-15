/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
package core.backend.fu.basis

import chisel3._
import chisel3.util._

import utils._

// 全加器迭代单元
class FullAdder_33 extends Module {
  val io = IO(new Bundle {
    val A = Input(UInt(1.W))
    val B = Input(UInt(1.W))
    val C = Input(UInt(1.W))
    val S = Output(UInt(1.W))
    val CG = Output(UInt(1.W))
    val CP = Output(UInt(1.W))
  })
  io.CG := io.A & io.B
  io.CP := io.A | io.B
  io.S := (io.CP & (~io.CG)) ^ io.C
}
// 定义一个FullAdder函数，调用最基本的一位全加器迭代单元
object FullAdder_33 {
  def apply(A: UInt, B: UInt, C: UInt = 0.U): (UInt, UInt, UInt) = {
    val m = Module(new FullAdder_33).io
    m.A := A
    m.B := B
    m.C := C
    (m.S, m.CG, m.CP)
  }
}

class FullAdder_32 extends Module {
  val io = IO(new Bundle {
    val A = Input(UInt(1.W))
    val B = Input(UInt(1.W))
    val Ci = Input(UInt(1.W))
    val S = Output(UInt(1.W)) // NOTE: 3 Gate Level
    val Co = Output(UInt(1.W)) // NOTE: 2 Gate Level
  })
  io.S := io.A ^ io.B ^ io.Ci
  io.Co := (io.A & io.B) | (io.A & io.Ci) | (io.B & io.Ci)
}
object FullAdder_32 {
  def apply(A: UInt, B: UInt, Ci: UInt = 0.U): (UInt, UInt) = {
    val m = Module(new FullAdder_32).io
    m.A := A
    m.B := B
    m.Ci := Ci
    (m.S, m.Co)
  }
}

class HalfAdder extends Module {
  val io = IO(new Bundle {
    val A = Input(UInt(1.W))
    val B = Input(UInt(1.W))
    val S = Output(UInt(1.W))
    val C = Output(UInt(1.W))
  })
  io.S := io.A ^ io.B
  io.C := io.A & io.B
}
object HalfAdder {
  def apply(A: UInt, B: UInt): (UInt, UInt) = {
    val m = Module(new HalfAdder).io
    m.A := A
    m.B := B
    (m.S, m.C)
  }
}
