package blackbox

import chisel3._
import chisel3.util._

import defs._
import utils._

class STATISTIC_CACHE extends BlackBox {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val id = Input(UInt(4.W))
        val stat = Input(UInt(2.W))
    })
}

class STATISTIC_BP extends BlackBox {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val predictValid = Input(Bool())
        val predictRight = Input(Bool())
    })
}

class STATISTIC_PRED_CHOICE extends BlackBox {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val pGlobal = Input(Bool())
        val pLocal = Input(Bool())
    })
}

class STATISTIC_FRONT_HUNGER extends BlackBox {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val ifu_hunger = Input(Bool())
    val idu_hunger = Input(Bool())
  })
}

class STATISTIC_BACK_HUNGER extends BlackBox {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val isu_hunger = Input(Bool())
    val exu_hunger = Input(Bool())
    val wbu_hunger = Input(Bool())
  })
}

//class STATISTIC_FB_HUNGER extends BlackBox {
//  val io = IO(new Bundle {
//    val clk = Input(Clock())
//    val rst = Input(Bool())
//    val front_hunger = Input(Bool())
//    val back_hunger = Input(Bool())
//  })
//}

//class STATISTIC_BUFFER extends BlackBox {
//    val io = IO(new Bundle {
//        val clk = Input(Clock())
//        val rst = Input(Bool())
//        val empty = Input(Bool())
//        val full = Input(Bool())
//    })
//}
