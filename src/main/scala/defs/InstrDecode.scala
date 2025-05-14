package defs

import chisel3._
import chisel3.util._

import defs._
import isa.riscv._
import isa.mips._
import utils._
import module.fu._
import isa.loongarch.LA_Base
import config._

/** Decode 類型定義
  */
trait HasRISCV_InstrType extends HasMarCoreParameter {
  def InstrN = "b0000".U
  def InstrI = "b0100".U
  def InstrR = "b0101".U
  def InstrS = "b0010".U
  def InstrB = "b0001".U
  def InstrU = "b0110".U
  def InstrJ = "b0111".U
  def InstrSA = "b1111".U // Atom Inst: SC

  def isrfWen(instrType: UInt): Bool = instrType(2)
}

/** Decode 類型定義
  *
  * 考慮到原手冊中, 1) 指令類型命名雜亂, 2)设计缺陷而表面上相似的编码, 3)LoongArch 本身指令類型的高度複雜,
  * 我們不採取手冊中的指令分化方式.
  *
  * 因此, 參考 https://github.com/loongson-community/loongarch-opcodes 進行設計
  */
trait HasLA32R_InstrType extends HasMarCoreParameter {
  def InstrN = "b0_0000".U
  def DJK = "b1_0000".U
  def DJUk5 = "b1_0000".U
  def DJUk12 = "b1_0000".U
  def DJSk12 = "b1_0000".U
  def DJSk16 = "b1_0000".U
  def Ud15 = "b0_0000".U
  def DSj20 = "b1_0000".U
  def Sd10k16 = "b1_0000".U
  def JSd5k16 = "b1_0000".U
  def JUd5Sk12 = "b1_0000".U

  def isrfWen(instrType: UInt): Bool = instrType(4)
}

/** Decode 類型定義
  */
trait HasMIPS_InstrType extends HasMarCoreParameter {
  def InstrI = "b00".U
  def InstrR = "b01".U
  def InstrJ = "b10".U

  def isrfWen(instrType: UInt): Bool = true.B
}

/** 這裏看似提供了三個類型, 但是需要注意的是, 在多數設計中, 往往只會向後端傳遞兩個操作數.
  */
object SrcType {
  def reg = "b0".U
  def pc = "b1".U
  def imm = "b1".U
  def apply() = UInt(1.W)
}

/** 執行單元的功能部件.
  *
  * @note
  *   - 在太多的設計中, 會將分支指令與 ALU 整合到一起; MUL和DIV整合到一起(MDU).
  *   - 我不遵循這個傳統出於以下原因:
  *     - BRU與ALU整合之後能節省等效於2-3個CLA的面積, 但是卻擠佔了ALU的執行可能性.
  *     - 但BRU指令在多數WorkLoad中數量很多, 多數的實踐中也將獨立BRU設計爲選配的.
  *     - 在設計新的架構的時候, 爲了代碼可閱讀性, 我將BRU實際上獨立設計了.
  *     - TODO: 未來重新提供BRU與ALU整合的設計.
  *
  *   - 不將MUL和DIV設計到一起的原因是:
  *     - MUL在2-3級流水後, 輕易符合20Tg延遲. 而Div的設計往往面積極大, 流水延遲極高.
  *     - MUL和DIV目前沒有較好的設計可以整合到一起(就像加法器那樣)
  *     - 因此, 編譯器往往提供模擬除法以適應在嵌入式中沒有實現乘法的情況.
  *     - 據此我將MUL和DIV分開設計.
  */
object FuType extends HasMarCoreConst {
  def num = 5
  def alu = "b000".U
  def bru = "b001".U
  def lsu = "b010".U
  def mulu = "b011".U
  def divu = "b100".U
  def csr = "b101".U
  def mou = "b101".U // 控制流類, 這個是之前的實現

  def apply() = UInt(log2Up(num).W)
}

/** Fu 控制信號. 在這裏規範了 Fu 控制信號應當是 7 bit 以內的.
  *
  * 任何大於7位控制信號的行爲都是不被允許的. 但是任何設計者都可以平衡是否使用全部 7 位信號.
  *
  * 當不希望使用到其中某些信號的時候, 請務必使得要拋棄的信號不與任何邏輯像關聯(常見需要避免的就是簡單的比較)
  */
object FuCtrl {
  def apply() = UInt(7.W)
}

/** RSICV 架構下的譯碼表.
  */
object InstructionsRISCV extends HasRISCV_InstrType with HasMarCoreParameter {
  def NOP = 0x00000013.U
  val DecodeDefault = List(InstrN, FuType.csr, CSRCtrl.jmp)
  def DecodeTable = RVIInstr.table ++ // MarCoreTrap.table ++
    RVMInstr.table ++ // (if (HasMExtension) RVMInstr.table else Array.empty) ++
    Priviledged.table ++
    RVZicsrInstr.table
}

/** MIPS 架構下的譯碼表
  */
object InstructionsMIPS extends HasMIPS_InstrType with HasMarCoreParameter {
  def NOP = 0x00000000.U
  val DecodeDefault = List(InstrR, FuType.alu, ALUCtrl.sll)
  def DecodeTable = MIPSInstrC3.table
}

/** LoongArch 架構下的譯碼表
  */
object InstructionsLA32R extends HasLA32R_InstrType with HasMarCoreParameter {
  def NOP = 0x3400000.U
  val DecodeDefault = List(Ud15, FuType.csr, CSRCtrl.jmp)
  def DecodeTable = LA_Base.table
}
