package module.fu.basis

import chisel3._
import chisel3.util._

import defs._
import utils._
import module.fu._
import module.fu.basis._

class Wallace8 extends MarCoreModule {
  val io = IO(new Bundle {
    val N = Input(UInt(8.W))
    val Cin = Input(UInt(6.W))
    val Cout = Output(UInt(6.W))
    val C = Output(UInt(1.W))
    val S = Output(UInt(1.W))
  })
  // Level 3 of 8 Bits Wallace Tree
  val (
    (l3_FA0_S, l3_FA0_C),
    (l3_FA1_S, l3_FA1_C),
    (l3_HA2_S, l3_HA2_C)
  ) = (
    FullAdder_32(io.N(0), io.N(1), io.N(2)),
    FullAdder_32(io.N(3), io.N(4), io.N(5)),
    HalfAdder(io.N(6), io.N(7))
  )
  // Level 2 of 8 Bits Wallace Tree
  val (
    (l2_FA0_S, l2_FA0_C),
    (l2_FA1_S, l2_FA1_C)
  ) = (
    FullAdder_32(l3_FA0_S, l3_FA1_S, l3_HA2_S),
    FullAdder_32(io.Cin(0), io.Cin(1), io.Cin(2))
  )
  // Level 1 of 8 Bits Wallace Tree
  val (
    (l1_FA0_S, l1_FA0_C)
  ) = (
    FullAdder_32(l2_FA0_S, l2_FA1_S, io.Cin(3))
  )
  // Level 0 of 8 Bits Wallace Tree
  val (_S, _C) = FullAdder_32(l1_FA0_S, io.Cin(4), io.Cin(5))
  io.S := _S
  io.C := _C
  val C_Vec = Wire(Vec(6, UInt(1.W)))
  C_Vec(0) := l3_FA0_C
  C_Vec(1) := l3_FA1_C
  C_Vec(2) := l3_HA2_C
  C_Vec(3) := l2_FA0_C
  C_Vec(4) := l2_FA1_C
  C_Vec(5) := l1_FA0_C
  io.Cout := C_Vec.asUInt
}

class Wallace16 extends MarCoreModule {
  val io = IO(new Bundle {
    val N = Input(UInt(16.W))
    val Cin = Input(UInt(14.W))
    val Cout = Output(UInt(14.W))
    val C = Output(UInt(1.W))
    val S = Output(UInt(1.W))
  })
  // Level 5 of 16 Bits Wallace Tree
  val (
    (l5_FA0_S, l5_FA0_C),
    (l5_FA1_S, l5_FA1_C),
    (l5_FA2_S, l5_FA2_C),
    (l5_FA3_S, l5_FA3_C),
    (l5_FA4_S, l5_FA4_C)
  ) = (
    FullAdder_32(io.N(0), io.N(1), io.N(2)),
    FullAdder_32(io.N(3), io.N(4), io.N(5)),
    FullAdder_32(io.N(6), io.N(7), io.N(8)),
    FullAdder_32(io.N(9), io.N(10), io.N(11)),
    FullAdder_32(io.N(12), io.N(13), io.N(14))
  )
  // Level 4 of 16 Bits Wallace Tree
  val (
    (l4_FA0_S, l4_FA0_C),
    (l4_FA1_S, l4_FA1_C),
    (l4_FA2_S, l4_FA2_C)
  ) = (
    FullAdder_32(l5_FA0_S, l5_FA1_S, l5_FA2_S),
    FullAdder_32(l5_FA3_S, l5_FA4_S, io.N(15)),
    FullAdder_32(io.Cin(0), io.Cin(1), io.Cin(2))
  )
  val wallace8 = Module(new Wallace8())
  val (_S, _C) = (wallace8.io.S, wallace8.io.C)
  io.S := _S
  io.C := _C
  val S_Vec = Wire(Vec(3, UInt(1.W)))
  S_Vec(0) := l4_FA0_S
  S_Vec(1) := l4_FA1_S
  S_Vec(2) := l4_FA2_S
  wallace8.io.N := Cat(io.Cin(7,3), S_Vec.asUInt)
  wallace8.io.Cin := io.Cin(13, 8)
  val C_Vec = Wire(Vec(8, UInt(1.W)))
  C_Vec(0) := l5_FA0_C
  C_Vec(1) := l5_FA1_C
  C_Vec(2) := l5_FA2_C
  C_Vec(3) := l5_FA3_C
  C_Vec(4) := l5_FA4_C
  C_Vec(5) := l4_FA0_C
  C_Vec(6) := l4_FA1_C
  C_Vec(7) := l4_FA2_C
  io.Cout := Cat(wallace8.io.Cout, C_Vec.asUInt)
}

class Wallace24 extends MarCoreModule {
  val io = IO(new Bundle {
    val N = Input(UInt(24.W))
    val Cin = Input(UInt(22.W))
    val Cout = Output(UInt(22.W))
    val C = Output(UInt(1.W))
    val S = Output(UInt(1.W))
  })
  // Level 6 of 24 Bits Wallace Tree
  val (
    (l6_FA0_S, l6_FA0_C),
    (l6_FA1_S, l6_FA1_C),
    (l6_FA2_S, l6_FA2_C),
    (l6_FA3_S, l6_FA3_C),
    (l6_FA4_S, l6_FA4_C),
    (l6_FA5_S, l6_FA5_C),
    (l6_FA6_S, l6_FA6_C),
    (l6_FA7_S, l6_FA7_C)
  ) = (
    FullAdder_32(io.N(0), io.N(1), io.N(2)),
    FullAdder_32(io.N(3), io.N(4), io.N(5)),
    FullAdder_32(io.N(6), io.N(7), io.N(8)),
    FullAdder_32(io.N(9), io.N(10), io.N(11)),
    FullAdder_32(io.N(12), io.N(13), io.N(14)),
    FullAdder_32(io.N(15), io.N(16), io.N(17)),
    FullAdder_32(io.N(18), io.N(19), io.N(20)),
    FullAdder_32(io.N(21), io.N(22), io.N(23)),
  )
  val wallace16 = Module(new Wallace16())
  val (_S, _C) = (wallace16.io.S, wallace16.io.C)
  io.S := _S
  io.C := _C
  val S_Vec = Wire(Vec(8, UInt(1.W)))
  S_Vec(0) := l6_FA0_S
  S_Vec(1) := l6_FA1_S
  S_Vec(2) := l6_FA2_S
  S_Vec(3) := l6_FA3_S
  S_Vec(4) := l6_FA4_S
  S_Vec(5) := l6_FA5_S
  S_Vec(6) := l6_FA6_S
  S_Vec(7) := l6_FA7_S
  wallace16.io.N := Cat(io.Cin(7, 0), S_Vec.asUInt)
  wallace16.io.Cin := io.Cin(21, 8)
  val C_Vec = Wire(Vec(8, UInt(1.W)))
  C_Vec(0) := l6_FA0_C
  C_Vec(1) := l6_FA1_C
  C_Vec(2) := l6_FA2_C
  C_Vec(3) := l6_FA3_C
  C_Vec(4) := l6_FA4_C
  C_Vec(5) := l6_FA5_C
  C_Vec(6) := l6_FA6_C
  C_Vec(7) := l6_FA7_C
  io.Cout := Cat(wallace16.io.Cout, C_Vec.asUInt)
}

class Wallace32 extends MarCoreModule {
  val io = IO(new Bundle {
    val N = Input(UInt(32.W))
    val Cin = Input(UInt(30.W))
    val Cout = Output(UInt(30.W))
    val C = Output(UInt(1.W))
    val S = Output(UInt(1.W))
  })
  // Level 7 of 32 Bits Wallace Tree
  val (
    (l7_FA0_S, l7_FA0_C),
    (l7_FA1_S, l7_FA1_C),
    (l7_FA2_S, l7_FA2_C),
    (l7_FA3_S, l7_FA3_C),
    (l7_FA4_S, l7_FA4_C),
    (l7_FA5_S, l7_FA5_C),
    (l7_FA6_S, l7_FA6_C),
    (l7_FA7_S, l7_FA7_C)
  ) = (
    FullAdder_32(io.N(0), io.N(1), io.N(2)),
    FullAdder_32(io.N(3), io.N(4), io.N(5)),
    FullAdder_32(io.N(6), io.N(7), io.N(8)),
    FullAdder_32(io.N(9), io.N(10), io.N(11)),
    FullAdder_32(io.N(12), io.N(13), io.N(14)),
    FullAdder_32(io.N(15), io.N(16), io.N(17)),
    FullAdder_32(io.N(18), io.N(19), io.N(20)),
    FullAdder_32(io.N(21), io.N(22), io.N(23))
  )
  val wallace24 = Module(new Wallace24())
  val (_S, _C) = (wallace24.io.S, wallace24.io.C)
  io.S := _S
  io.C := _C
  val S_Vec = Wire(Vec(8, UInt(1.W)))
  S_Vec(0) := l7_FA0_S
  S_Vec(1) := l7_FA1_S
  S_Vec(2) := l7_FA2_S
  S_Vec(3) := l7_FA3_S
  S_Vec(4) := l7_FA4_S
  S_Vec(5) := l7_FA5_S
  S_Vec(6) := l7_FA6_S
  S_Vec(7) := l7_FA7_S
  wallace24.io.N := Cat(io.Cin(15, 0), io.N(31, 24), S_Vec.asUInt)
  wallace24.io.Cin := io.Cin(29, 16)
  val C_Vec = Wire(Vec(8, UInt(1.W)))
  C_Vec(0) := l7_FA0_C
  C_Vec(1) := l7_FA1_C
  C_Vec(2) := l7_FA2_C
  C_Vec(3) := l7_FA3_C
  C_Vec(4) := l7_FA4_C
  C_Vec(5) := l7_FA5_C
  C_Vec(6) := l7_FA6_C
  C_Vec(7) := l7_FA7_C
  io.Cout := Cat(wallace24.io.Cout, C_Vec.asUInt)
}

class WallaceTree(n: Int) extends MarCoreModule {
  val io = IO(new Bundle {
    val N = Input(UInt(n.W))
    val Cin = Input(UInt((n-2).W))
    val Cout = Output(UInt((n-2).W))
    val C = Output(UInt(1.W))
    val S = Output(UInt(1.W))
  })

  if (n == 8) {
    val wallaceTree = Module(new Wallace8())
    wallaceTree.io.N := io.N
    wallaceTree.io.Cin := io.Cin
    io.Cout := wallaceTree.io.Cout
    io.C := wallaceTree.io.C
    io.S := wallaceTree.io.S
  } else if (n == 16) {
    val wallaceTree = Module(new Wallace16())
    wallaceTree.io.N := io.N
    wallaceTree.io.Cin := io.Cin
    io.Cout := wallaceTree.io.Cout
    io.C := wallaceTree.io.C
    io.S := wallaceTree.io.S
  } else if (n == 32) {
    val wallaceTree = Module(new Wallace32())
    wallaceTree.io.N := io.N
    wallaceTree.io.Cin := io.Cin
    io.Cout := wallaceTree.io.Cout
    io.C := wallaceTree.io.C
    io.S := wallaceTree.io.S
  } else {
    io.S := 0.U
    io.C := 0.U
    io.Cout := 0.U
    println(prompt+blink+redBG+"ERROR@Wallace"+resetColor)
  }
}
object WallaceTree {
  def apply(n: Int, N: UInt, Cin: UInt): (UInt, UInt, UInt) = {
    val m = Module(new WallaceTree(n)).io
    m.N := N
    m.Cin := Cin
    (m.S, m.C, m.Cout)
  }
}
