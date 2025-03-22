package defs

import chisel3._
import chisel3.util._

import defs._
import isa.riscv._
import isa.mips._
import utils._
import module.fu._

/* Decode */
trait HasInstrType extends HasMarCoreParameter {
	def InstrN		= "b0000".U
	def InstrI		= "b0100".U
	def InstrR		= "b0101".U
	def InstrS		= "b0010".U
	def InstrB		= "b0001".U
	def InstrU		= "b0110".U
	def InstrJ		= "b0111".U
	def InstrSA		= "b1111".U // Atom Inst: SC

	def isrfWen(instrType: UInt): Bool = instrType(2)
}

//trait HasLA32R_InstrType extends HasMarCoreParameter {
//	def Instr2R		= "b1000".U
//	def Instr3R		= "b1001".U
//	def Instr4R		= "b1010".U
//	def Instr2RI5	= "b1011".U
//	def Instr2RI12	= "b1100".U
//	def Instr2RI14	= "b1101".U
//	def Instr2RI16	= "b1110".U
//	def Instr1RI20	= "b1111".U // Fixme maybe
//	def Instr1RI21	= "b0000".U
//	def InstrI26	= "b0001".U
//	def Instr2RI12S	= "b0100".U // For store type instr, just don't write to regfile
//	def InstrDontC	= "b0010".U // DontCare Instr, Same as nop(just for current)
//	def InstrN		= "b0111".U
//	//-------------
////	def InstrPreld	= "b0010".U // For PRELD
////	def BAR			= "b0011".U
//
//	def isrfWen(instrType: UInt): Bool = instrType(4)
//}

trait HasMips32_InstrType extends HasMarCoreParameter {
	def InstrI		= "b0100".U
	def InstrR		= "b0101".U
	def InstrJ		= "b0110".U

	def isrfWen(instrType: UInt): Bool = instrType(2)
}

object SrcType {
	def reg	= "b0".U
	def pc	= "b1".U
	def imm	= "b1".U
	def apply() = UInt(1.W)
}

object FuType extends HasMarCoreConst {
	def num	= 5
	def alu	= "b000".U
	def lsu	= "b001".U
	def mdu	= "b010".U
	def csr	= "b011".U
	def mou	= "b100".U
	def bru	= if(IndependentBru) "b101".U else alu

	def apply() = UInt(log2Up(num).W)
}

object FuCtrl {
	def apply() = UInt(7.W)
}

object Instructions extends HasInstrType with HasMarCoreParameter {
	def NOP = 0x00000013.U
	val DecodeDefault = List(InstrN, FuType.csr, CSRCtrl.jmp)
	def DecodeTable = RVIInstr.table ++ MarCoreTrap.table ++
		(if (HasMExtension) RVMInstr.table else Array.empty) ++
		Priviledged.table ++
		RVZicsrInstr.table
}

object InstructionsMIPS extends HasInstrType with HasMarCoreParameter {
	def NOP = 0x00000000.U
	val DecodeDefault = List(InstrR, FuType.alu, ALUCtrl.sll)
	def DecodeTable = MIPSInstrC3.table
}

//object InstructionsLA32R extends HasLA32R_InstrType with HasMarCoreParameter {
//	def NOP = 0x3400000.U
//	val DecoudeDefault = List(InstrN, FuType.csr, CSRCtrl.jmp) // Wrong
//	def DecodeTable = LA_Base.table
//}
