package module.fu

import chisel3._
import chisel3.util._

import defs._
import utils._
import module.fu.basis._

// 超前进位逻辑模块
// 使用Vec(4,UInt(1.W))，必须逐个赋值
class CPG extends MarCoreModule {
  val io = IO(new Bundle {
    val CI = Input(UInt(1.W))
    val CG = Input(Vec(4, UInt(1.W)))
    val CP = Input(Vec(4, UInt(1.W)))

    val CO = Output(Vec(3, UInt(1.W)))
    val CP4 = Output(UInt(1.W))
    val CG4 = Output(UInt(1.W))
  })
  io.CO(0) := io.CG(0) | (io.CP(0) & io.CI)
  io.CO(1) := io.CG(1) | (io.CP(1) & io.CG(0)) | (io.CP(1) & io.CP(0) & io.CI)
  io.CO(2) := io.CG(2) | (io.CP(2) & io.CG(1)) |
              (io.CP(2) & io.CP(1) & io.CG(0)) |
              (io.CP(2) & io.CP(1) & io.CP(0) & io.CI)
  io.CP4 := io.CP(0) & io.CP(1) & io.CP(2) & io.CP(3)
  io.CG4 := io.CG(3) | (io.CP(3) & io.CG(2)) |
            (io.CP(3) & io.CP(2) & io.CG(1)) |
            (io.CP(3) & io.CP(2) & io.CP(1) & io.CG(0))
}

// 伴生对象
object CPG {
  def apply(CI: UInt, CG: Vec[UInt], CP: Vec[UInt]) = {
    val m = Module(new CPG).io
    m.CI := CI
    m.CP := CP
    m.CG := CG
    (m.CO, m.CG4, m.CP4)
  }
}

class AdderGen(n:Int) extends MarCoreModule {
  require(n == 4 || n == 16 || n == 32 || n == 64 || n == 128)
  val io = IO(new Bundle {
    val A = Input(UInt(n.W))
    val B = Input(UInt(n.W))
    val CI = Input(UInt(1.W))
    val S = Output(UInt(n.W))
    val CO = Output(UInt(1.W))
  })

  // AdderX，类似模板函数，是CLA中某一级的数据通路，根据传入的Adder函数，生成不同的函数
  def AdderX(A: UInt, B: UInt, CI: UInt, Adder: (UInt,UInt,UInt) => (UInt,UInt,UInt), n: Int) = {
    val k = n/4 // 三级嵌套结构中，每一个迭代单元的输入和输出都有16位;2级有4位;1级有1位

    val S_Vec = Wire(Vec(4, UInt(k.W))) // 每一级里都由4个迭代单元和一个CPG组成，所以都切成4段
    val CG    = Wire(Vec(4, UInt(1.W)))
    val CP    = Wire(Vec(4, UInt(1.W)))

    val (co, cg4, cp4) = CPG(CI, CG, CP)

    for (i <- 0 to 3) {
      val (s, cg, cp) = Adder(A(k-1+k*i,k*i), B(k-1+k*i,k*i), if (i == 0) CI else co(i-1))
      S_Vec(i) := s // 模板函数参数较多，可读性较差
      CG(i) := cg
      CP(i) := cp
    }

    val S = S_Vec.asUInt

    (S,cg4,cp4)
  }

  // 利用scala特性——部分应用函数，将AdderX例化为具体的函数
  val CLAdder4  = AdderX(_:UInt, _:UInt, _:UInt, FullAdder_33.apply, 4 )
  val CLAdder16 = AdderX(_:UInt, _:UInt, _:UInt, CLAdder4 , 16)
  val CLAdder64 = AdderX(_:UInt, _:UInt, _:UInt, CLAdder16, 64)

  // 对n进行判断，生成对应位数的加法器
  if (n == 4) {
    val (s, cg, cp) = CLAdder4(io.A, io.B, io.CI)
    io.S := s
    io.CO := cg | (cp & io.CI)
  } else if (n == 16) { // NOTE: 10级门级延迟
    val (s, cg, cp) = CLAdder16(io.A, io.B, io.CI)
    io.S := s
    io.CO := cg | (cp & io.CI)
  } else if (n == 32) {
    val (sL, cgL, cpL) = CLAdder16(io.A(15,0) , io.B(15,0) , io.CI)
    val ciH = cgL | (cpL & io.CI)
    val (sH, cgH, cpH) = CLAdder16(io.A(31,16), io.B(31,16), ciH)
    io.S := Cat(sH, sL)
    io.CO := cgH | (cpH & ciH)
  } else if (n == 64) { // NOTE: 20级门级延迟
    val (s, cg, cp) = CLAdder64(io.A, io.B, io.CI)
    io.S := s
    io.CO := cg | (cp & io.CI)
  } else if (n == 128) {
    // TODO: TEST
    val (sL, cgL, cpL) = CLAdder64(io.A(63, 0), io.B(63, 0), io.CI)
    val ciH = cgL | (cpL & io.CI)
    val (sH, cgH, cpH) = CLAdder64(io.A(127, 64), io.B(127, 64), ciH)
    io.S := Cat(sH, sL)
    io.CO := cgH | (cpH & io.CI)
  } else {
    println(prompt+blink+redBG+"ERROR@CLA"+resetColor)
  }
}

object AdderGen {
  def apply(n: Int, A:UInt, B:UInt, CI: UInt) = {
    val m = Module(new AdderGen(n)).io
    m.A := A
    m.B := B
    m.CI := CI
    (m.S, m.CO)
  }
}
