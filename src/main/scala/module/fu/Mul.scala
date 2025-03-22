package module.fu

import chisel3._
import chisel3.util._

import defs._
import utils._
import module.fu._
import module.fu.basis._
import top._

// TODO: 完成对Mul的参数流水线，并在实现模块中尽量解偶，分模块测试

class MulStage1IO(len: Int) extends MarCoreBundle {
  val switchT = Output(Vec(len*2, UInt((len/2).W)))
  val carry = Output(Vec(len/2, UInt(1.W)))
}
class MultiplierStage1(len: Int) extends MarCoreModule {
  implicit val moduleName: String = this.name
  require(len == 16 || len == 32 || len == 64)
  val io = IO(new Bundle {
	  val in = Flipped(Vec(2, Output(UInt(len.W))))
	  val sign = Input(Bool())
    val out = new MulStage1IO(len)
    val switch = Output(Vec(len/2, UInt((len*2).W)))
  })
  val X = io.in(0)
  val Y = io.in(1)
  val switch = Wire(Vec(len/2, UInt((2*len).W)))
//  val carry  = Wire(Vec(len/2, UInt(1.W)))
  for (i <- 0 until len/2) {
    val (_out, _carry) = BoothCore(len, X, 
      if(i==0) Cat(Y(2*i+1, 2*i), 0.U(1.W))
      else     Y(2*i+1, 2*i-1))
    io.out.carry(i) := _carry
    if(i==0) switch(i) := SignExt(_out, (len*2))
    else     switch(i) := SignExt(Cat(_out, 0.U((i*2).W)), (len*2))
  }

  // 经过翻转后的矩阵
  val switchT = Wire(Vec(len*2, UInt((len/2).W)))
  val switchT_Vec = Wire(Vec(len*2, Vec(len/2, UInt(1.W))))
  for (i <- 0 until len*2) {
    for (j <- 0 until len/2) switchT_Vec(i)(j) := switch(j)(i)

    switchT(i) := switchT_Vec(i).asUInt
  }
  io.out.switchT := switchT
  io.switch := switch
}

class MulStage2IO(len: Int) extends MarCoreBundle {
  val S = Output(Vec(len*2, UInt(1.W)))
  val C = Output(Vec(len*2, UInt(1.W)))
  val c2AddrIn = Output(UInt(1.W))
  val c2AddrC  = Output(UInt(1.W))
}
class MultiplierStage2(len: Int) extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new Bundle {
    val in = Flipped(new MulStage1IO(len))
    val out = new MulStage2IO(len)
  })
  val adderS = Wire(Vec(len*2, UInt(1.W)))
  val adderC = Wire(Vec(len*2, UInt(1.W)))
  val trees = Array.tabulate(len*2)(i => Module(new WallaceTree(len/2)))
  for (i <- 0 until len*2) {
    trees(i).io.N := io.in.switchT(i)
    val cin =
      if (i==0) io.in.carry.asUInt(len/2-3,0)
      else trees(i-1).io.Cout
    trees(i).io.Cin := cin
    adderS(i) := trees(i).io.S
    adderC(i) := trees(i).io.C
  }

  io.out.S := adderS
  io.out.C := adderC
  io.out.c2AddrIn := io.in.carry(len/2-2)
  io.out.c2AddrC  := io.in.carry(len/2-1)
}

class MultiplierStage3(len: Int) extends MarCoreModule {
  val io = IO(new Bundle {
    val in = Flipped(new MulStage2IO(len))
	  val out = Output(UInt((len * 2).W))
  })
  val (outS, outC) = AdderGen(len*2, io.in.S.asUInt,
    Cat(io.in.C.asUInt(len*2-2,0), io.in.c2AddrIn), io.in.c2AddrC
  )
  io.out := outS
}

class Multiplier_Test(len: Int) extends MarCoreModule {
  implicit val moduleName: String = this.name
  require(len == 16 || len == 32 || len == 64)
  val io = IO(new MulDivIO(len))
  val stage1 = IO(Output(Vec(len/2, UInt((len*2).W))))
  val carry = IO(Output(Vec(len/2, UInt(1.W))))
  val S = IO(Output(UInt((2*len).W)))
  val C = IO(Output(UInt((2*len).W)))
  val Cin = IO(Output(UInt(1.W)))
  if (Settings.get("ImplBetterMultiplier")) {
    val multiplierStage1 = Module(new MultiplierStage1(len))
    val multiplierStage2 = Module(new MultiplierStage2(len))
    val multiplierStage3 = Module(new MultiplierStage3(len))
    stage1 := multiplierStage1.io.switch
    carry := multiplierStage1.io.out.carry
    S := multiplierStage2.io.out.S.asUInt
    C := Cat(multiplierStage2.io.out.C.asUInt(len*2-2,0),
      multiplierStage2.io.out.c2AddrIn)
    Cin := multiplierStage2.io.out.c2AddrC

    val valid_stg1 = io.in.valid
    io.in.ready := io.in.valid

    multiplierStage1.io.in := io.in.bits
    multiplierStage1.io.sign := io.sign
    // STATE1
    val valid_stg2 = RegNext(valid_stg1)
    multiplierStage2.io.in := RegEnable(multiplierStage1.io.out, valid_stg1)

    // STATE2
    val valid_stg3 = RegNext(valid_stg2)
    multiplierStage3.io.in := RegEnable(multiplierStage2.io.out, valid_stg2)

    // STATE3
    io.out.valid := valid_stg3
    io.out.bits := multiplierStage3.io.out
  } else {
    val mulRes = (io.in.bits(0).asSInt * io.in.bits(1).asSInt).asSInt
  	io.out.bits := mulRes.asUInt
  	io.out.valid := true.B
  	io.in.ready := io.in.valid
  }
}
