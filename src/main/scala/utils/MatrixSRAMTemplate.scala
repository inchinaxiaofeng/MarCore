package utils

import chisel3._
import chisel3.util._
import module.cache._

class MatrixReg(row: Int, col: Int, width: Int) extends Module {
	val io = IO(new Bundle {
		val row_addr = Input(UInt(log2Ceil(row).W))
		val col_addr = Input(UInt(log2Ceil(col).W))
		val access = Input(Bool())
		val lru_row = Output(UInt(row.W))
	})

	// entries X entries matrix
	val matrix = RegInit(Vec(row, Vec(col, 0.U(width.W))))

	// Update
	when (io.access) {
		for (c <- 0 until col) {
			matrix(io.row_addr)(c) := 1.U
		}
		for (r <- 0 until row) {
			matrix(r)(io.col_addr) := 0.U
		}
	}

	io.lru_row := PriorityEncoder(matrix.map(row => !row.reduce(_&_)))
}

//// SRAM模块
//class SRAM(depth: Int, width: Int) extends Module {
//	val io = IO(new Bundle {
//		val addr = Input(UInt(log2Ceil(depth).W))
//		val din = Input(UInt(width.W))
//		val dout = Output(UInt(width.W))
//		val we = Input(Bool())
//		val re = Input(Bool())
//	})
//	val mem = SyncReadMem(depth, UInt(width.W))
//	when (io.we) { mem.write(io.addr, io.din) }
//	io.dout := mem.read(io.addr, io.re)
//}

// 矩阵内存管理模块
//class MatrixMemory(rows: Int, cols: Int, width: Int) extends Module {
//	val io = IO(new Bundle {
//		val row_addr = Input(UInt(log2Ceil(rows).W))
//		val col_addr = Input(UInt(log2Ceil(cols).W))
//		val din = Input(UInt(width.W))
//		val dout = Output(UInt(width.W))
//		val we = Input(Bool())
//		val re = Input(Bool())
//	})
//	
//	val sram = Module(new SRAM(rows * cols, width))
//	
//	// 行地址转换为线性地址
//	val row_addr = io.row_addr * cols.U
//	// 列地址转换为线性地址
//	val col_addr = io.col_addr
//	
//	sram.io.addr := row_addr + col_addr
//	sram.io.din := io.din
//	io.dout := sram.io.dout
//	sram.io.we := io.we
//	sram.io.re := io.re
//}
