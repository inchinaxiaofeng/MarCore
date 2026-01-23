package module.fu.basis

import chisel3._
import chisel3.util._

import defs._
import utils._
import top._

// Booth两位乘-选择信号生成逻辑
class BoothGen extends MarCoreModule {
  implicit val module: String = this.name
  val io = IO(new Bundle {
    val Y = Input(UInt(3.W))
    val S = Output(UInt(4.W)) // (pos2, neg2, pos1, neg1)
  })
  val y = io.Y
  val ny = ~io.Y
  val S_Vec = Wire(Vec(4, UInt(1.W)))
  if (Settings.get("ImplBetterLogic")) {
    S_Vec(0) := ~(
      ~( ny(0) & y(1) & y(2) ) &
      ~( y(0) & ny(1) & y(2) )
    )
    S_Vec(1) := ~(
      ~( ny(0) & y(1) & ny(2) ) &
      ~( y(0) & ny(1) & ny(2) )
    )
    S_Vec(2) := ~(
      ~( ny(0) & ny(1) & y(2) )
    )
    S_Vec(3) := ~(
      ~( y(0) & y(1) & ny(2) )
    )
  } else {
    S_Vec(0) := (
      ( ny(0) & y(1) & y(2) ) |
      ( y(0) & ny(1) & y(2) )
    )
    S_Vec(1) := (
      ( ny(0) & y(1) & ny(2) ) |
      ( y(0) & ny(1) & ny(2) )
    )
    S_Vec(2) := ( ny(0) & ny(1) & y(2) )
    S_Vec(3) := ( y(0) & y(1) & ny(2) )
  }
  io.S := S_Vec.asUInt
}

// Booth两位乘结果选择逻辑
class BoothSel extends MarCoreModule {
  implicit val module: String = this.name
  val io = IO(new Bundle {
    val P  = Output(UInt(1.W))
    val X = Input(UInt(1.W))
    val Xo = Output(UInt(2.W)) // (pos2, neg2)
    val Xi = Input (UInt(2.W)) // (pos2, neg2)
    val S  = Input (UInt(4.W)) // (pos2, neg2, pos1, neg1)
  })
  val x = io.X
  val nx = ~io.X
  val Xo_Vec = Wire(Vec(2, UInt(1.W)))
  Xo_Vec(0) := nx
  Xo_Vec(1) := x
  io.Xo := Xo_Vec.asUInt
  if (Settings.get("ImplBetterLogic")) {
    io.P := ~(
      ~( io.S(0) & nx ) &
      ~( io.S(1) & x  ) &
      ~( io.S(2) & io.Xi(0) ) &
      ~( io.S(3) & io.Xi(1) )
    )
  } else {
    io.P := (
      (io.S(0) & nx) |
      (io.S(1) & x ) |
      (io.S(2) & io.Xi(0)) |
      (io.S(3) & io.Xi(1))
    )
  }
}

// Booth核心计算单元，负责某一压缩
class BoothCore(n: Int) extends MarCoreModule {
  implicit val module: String = this.name
  val io = IO(new Bundle {
    val P = Output(UInt(n.W))
    val X = Input(UInt(n.W))
    val C = Output(UInt(1.W))
    val Y = Input(UInt(3.W)) // (y_i+1, y_i, y_i-1)
  })
  val gen = Module(new BoothGen())
  gen.io.Y := io.Y
  io.C := gen.io.S(0) | gen.io.S(2)

  val sels = Array.tabulate(n)(i => Module(new BoothSel()))
  val P_Vec = Wire(Vec(n, UInt(1.W)))
  for (i <- 0 until n) {
    sels(i).io.S := gen.io.S
    sels(i).io.X := io.X(i)
    P_Vec(i) := sels(i).io.P
    val xi = 
      if (i==0) 0.U
      else sels(i-1).io.Xo
    sels(i).io.Xi := xi
  }
  io.P := P_Vec.asUInt
}
object BoothCore {
  def apply(n: Int, X: UInt, Y: UInt): (UInt, UInt) = {
    val m = Module(new BoothCore(n)).io
    m.X := X
    m.Y := Y
    (m.P, m.C)
  }
}
