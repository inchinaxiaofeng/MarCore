package module.fu

import chisel3._
import chisel3.util._

import defs._
import utils._
import top.Settings

class LoongArchALU extends MarCoreModule {
  implicit val moduleName: String = this.name
  val io = IO(new ALUIO)

  val (valid, srcA, srcB, ctrl) =
    (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
  def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
    this.valid := valid
    this.srcA := srcA
    this.srcB := srcB
    this.ctrl := ctrl
    io.out.bits
  }

  val isAddrSub = ALUCtrl.isSub(ctrl)
  val (adderRes, adderCarry) =
    AdderGen(XLEN, srcA, (srcB ^ Fill(XLEN, isAddrSub)), isAddrSub)

  val xorRes = srcA ^ srcB
  val andRes = srcA & srcB
  val orRes = srcA | srcB
  val norRes = !orRes

  val sltu = !adderCarry
  val slt = xorRes(XLEN - 1) ^ sltu

  val shamt = srcB(4, 0)
  val res = MuxLookup(ALUCtrl.getEncoded(ctrl), adderRes)(
    Seq(
      ALUCtrl.getEncoded(ALUCtrl.sll) -> ((srcA << shamt)(XLEN - 1, 0)),
      ALUCtrl.getEncoded(ALUCtrl.srl) -> (srcA >> shamt),
      ALUCtrl.getEncoded(ALUCtrl.sra) -> ((srcA.asSInt >> shamt).asUInt),
//
      ALUCtrl.getEncoded(ALUCtrl.slt) -> ZeroExt(slt, XLEN), // 對 Bool 值進行0拓展
      ALUCtrl.getEncoded(ALUCtrl.sltu) -> ZeroExt(sltu, XLEN), // 對 Bool 值進行0拓展
//
      ALUCtrl.getEncoded(ALUCtrl.or) -> orRes,
      ALUCtrl.getEncoded(ALUCtrl.and) -> andRes,
      ALUCtrl.getEncoded(ALUCtrl.nor) -> norRes,
      ALUCtrl.getEncoded(ALUCtrl.xor) -> xorRes
    )
  )
  val aluRes = res

  io.out.bits := aluRes
  io.in.ready := io.out.ready
  io.out.valid := valid
}
