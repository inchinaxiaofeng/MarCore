//package isa.loongarch
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import module.fu.ALUCtrl
//
//object LA32R_FloatArithInstr extends HasInstrType {
//	def FADDS		= BitPat("00_00000_10000_00001_?????_?????_?????")
//	def FAADD		= BitPat("00_00000_10000_00010_?????_?????_?????")
//	def FSUBS		= BitPat("00_00000_10000_00101_?????_?????_?????")
//	def FSUBD		= BitPat("00_00000_10000_00110_?????_?????_?????")
//	def FMULS		= BitPat("00_00000_10000_01001_?????_?????_?????")
//	def FMULD		= BitPat("00_00000_10000_01010_?????_?????_?????")
//	def FDIVS		= BitPat("00_00000_10000_01101_?????_?????_?????")
//	def FDIVD		= BitPat("00_00000_10000_01110_?????_?????_?????")
//	def FMADDS		= BitPat("00_00100_00001_?????_?????_?????_?????")
//	def FMADDD		= BitPat("00_00100_00010_?????_?????_?????_?????")
//	def FNMADDS		= BitPat("00_00100_01001_?????_?????_?????_?????")
//	def FNMADDD		= BitPat("00_00100_01010_?????_?????_?????_?????")
//	def FNMSUBS		= BitPat("00_00100_01101_?????_?????_?????_?????")
//	def FNMSUBD		= BitPat("00_00100_01110_?????_?????_?????_?????")
//
//	def FMAXS		= BitPat("00_00000_10000_10001_?????_?????_?????")
//	def FMAXD		= BitPat("00_00000_10000_10010_?????_?????_?????")
//	def FMINS		= BitPat("00_00000_10000_10101_?????_?????_?????")
//	def FMIND		= BitPat("00_00000_10000_10110_?????_?????_?????")
//
//	def FMAXAS		= BitPat("00_00000_10000_11001_?????_?????_?????")
//	def FMAXAD		= BitPat("00_00000_10000_11010_?????_?????_?????")
//	def FMINAS		= BitPat("00_00000_10000_11101_?????_?????_?????")
//	def FMINAD		= BitPat("00_00000_10000_11110_?????_?????_?????")
//
//	def FABSS		= BitPat("00_00000_10001_01000_00001_?????_?????")
//	def FABSD		= BitPat("00_00000_10001_01000_00010_?????_?????")
//	def FNEGS		= BitPat("00_00000_10001_01000_00101_?????_?????")
//	def FNEGD		= BitPat("00_00000_10001_01000_00110_?????_?????")
//
//	def FSQRTS		= BitPat("00_00000_10001_01000_10001_?????_?????")
//	def FSQRTD		= BitPat("00_00000_10001_01000_10010_?????_?????")
//	def FRECIPS		= BitPat("00_00000_10001_01000_10101_?????_?????")
//	def FRECIPD		= BitPat("00_00000_10001_01000_10110_?????_?????")
//	def FRSQRTS		= BitPat("00_00000_10001_01000_11001_?????_?????")
//	def FRSQRTD		= BitPat("00_00000_10001_01000_11010_?????_?????")
//
//	def FCOPYSIGNS	= BitPat("00_00000_10001_00101_?????_?????_?????")
//	def FCOPYSIGND	= BitPat("00_00000_10001_00110_?????_?????_?????")
//
//	def FCLASSS		= BitPat("00_00000_10001_01000_01101_?????_?????")
//	def FCLASSD		= BitPat("00_00000_10001_01000_01110_?????_?????")
//}
//
//object LA32R_FloatCompareInstr extends HasInstrType {
//	def FCMPCONDS	= BitPat("00_00110_00001_?????_?????_?????_00???")
//	def FCMPCONDD	= BitPat("00_00110_00010_?????_?????_?????_00???")
//}
//
//object LA32R_FloatTransInstr extends HasInstrType {
//	def FCVTSD		= BitPat("00_00000_10001_10010_00110_?????_?????")
//	def FCVTDS		= BitPat("00_00000_10001_10010_01001_?????_?????")
//
//	def FFINTSW		= BitPat("00_00000_10001_11010_00100_?????_?????")
//	def FTINTWS		= BitPat("00_00000_10001_10110_00001_?????_?????")
//	def FFINTDW		= BitPat("00_00000_10001_11010_01000_?????_?????")
//	def FTINTWD		= BitPat("00_00000_10001_10110_00010_?????_?????")
//
//	def FTINTRMWS	= BitPat("00_00000_10001_10100_00001_?????_?????")
//	def FTINTRPWS	= BitPat("00_00000_10001_10100_10001_?????_?????")
//	def FTINTRMWD	= BitPat("00_00000_10001_10100_00010_?????_?????")
//	def FTINTRPWD	= BitPat("00_00000_10001_10100_10010_?????_?????")
//	def FTINTRZWS	= BitPat("00_00000_10001_10101_00001_?????_?????")
//	def FTINTRNEWS	= BitPat("00_00000_10001_10101_10001_?????_?????")
//	def FTINTRZWD	= BitPat("00_00000_10001_10101_00010_?????_?????")
//	def FTINTRNEWD	= BitPat("00_00000_10001_10101_10010_?????_?????")
//}
//
//object LA32R_MoveInstr extends HasInstrType {
//	def FMOVS		= BitPat("00_00000_10001_01001_00101_?????_?????")
//	def FMOVD		= BitPat("00_00000_10001_01001_00110_?????_?????")
//
//	def FSEL		= BitPat("00_00110_10000_00???_?????_?????_?????")
//
//	def MOVGR2FRW	= BitPat("00_00000_10001_01001_01001_?????_?????")
//	def MOVGR2FRHW	= BitPat("00_00000_10001_01001_01011_?????_?????")
//
//	def MOVFR2GRS	= BitPat("00_00000_10001_01001_01101_?????_?????")
//	def MOVFRH2GRS	= BitPat("00_00000_10001_01001_01111_?????_?????")
//	
//	def MOVGR2FCSR	= BitPat("00_00000_10001_01001_10000_?????_?????")
//	def MOVFCSR2GR	= BitPat("00_00000_10001_01001_10010_?????_?????")
//
//	def MOVFR2CF	= BitPat("00_00000_10001_01001_10100_?????_00???")
//	def MOVCF2FR	= BitPat("00_00000_10001_01001_10101_00???_?????")
//
//	def MOVGR2CF	= BitPat("00_00000_10001_01001_10110_?????_00???")
//	def MOVCF2GR	= BitPat("00_00000_10001_01001_10111_00???_?????")
//}
//
//object LA32R_BranchInstr extends HasInstrType {
//	def BCEQZ		= BitPat("01_0010?_?????_?????_?????_00???_?????")
//	def BCNEZ		= BitPat("01_0010?_?????_?????_?????_01???_?????")
//}
//
//object LA32R_AccessInstr extends HasInstrType {
//	def FLDS		= BitPat("00_10101_100??_?????_?????_?????_?????")
//	def FLDD		= BitPat("00_10101_110??_?????_?????_?????_?????")
//	def FSTS		= BitPat("00_10101_101??_?????_?????_?????_?????")
//	def FSTD		= BitPat("00_10101_111??_?????_?????_?????_?????")
//}
