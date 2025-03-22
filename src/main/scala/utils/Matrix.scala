package utils

import chisel3._
import chisel3.util._

class MatrixTwithWidth(row: Int, col: Int, width: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(row, Vec(col, UInt(width.W))))
    val out = Input(Vec(col, Vec(row, UInt(width.W))))
  })

  for (r <- 0 until row)
    for (c <- 0 until col)
      io.out(c)(r) := io.in(r)(c)
}

class MatrixT(row: Int, col: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(row, UInt(col.W)))
    val out = Output(Vec(col, UInt(row.W)))
  })

  val out_Vec = Wire(Vec(col, Vec(row, UInt(1.W))))
  for (r <- 0 until row) {
    for (c <- 0 until col) {
      out_Vec(c)(r) := io.in(r)(c)
    }
  }

  for (c <- 0 until col) {
    io.out(c) := out_Vec(c).asUInt
  }
}

//object MatrixT {
//  def apply(row: Int, col: Int, width: Int, matrix: Vec[UInt]): Vec[UInt] {
//    val m = Module(new MatrixTwithWidth(row, col, width)).io
//    m.in := matrix
//    m.out
//  }
//  def apply(row: Int, col: Int, matrix: Vec[UInt]): Vec[UInt] {
//    val m = Module(new MatrixT(row, col)).io
//    m.in := matrix
//    m.out
//  }
//}
