/*
** 2025 May 1
**
** The author disclaims copyright to this source code.  In place of
** a legal notice, here is a blessing:
**
**    May you do good and not evil.
**    May you find forgiveness for yourself and forgive others.
**    May you share freely, never taking more than you give.
**
 */
package core.backend.fu

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import utils.fu._
import config._

import core.uarch.fu.ALUCtrl

class ALU(implicit val p: MarCoreConfig) extends MarCoreFuModule {
  implicit val moduleName: String = this.name

  // ==== Caculate Logic ====
  // === Rename Ctrl Sig ===
  val isInvert = ALUCtrl.isInvert(ctrl)
  val isWord = ALUCtrl.isWord(ctrl)
  val isUnsign = ALUCtrl.isUnsign(ctrl)

  // === Addr gen ===
  val isAddrSub = isInvert
  val (adderRes, adderCarry) =
    AdderGen(XLEN, srcA, (srcB ^ Fill(XLEN, isAddrSub)), isAddrSub)

  // === Logic ===
  val xorRes = srcA ^ srcB
  val andRes = srcA & srcB
  val orRes = srcA | srcB
  val norRes = ~orRes

  // === Cmp ====
  val sltu = !adderCarry
  val slt = xorRes(XLEN - 1) ^ sltu

  // === Shift ===
  val shsrc = Mux(
    isUnsign,
    ZeroExt(srcA(31, 0), XLEN),
    SignExt(srcA(31, 0), XLEN)
  )
  val shamt = Mux(
    isWord,
    srcB(4, 0),
    if (XLEN == 64) srcB(5, 0) else srcB(4, 0)
  )
  val shout = Mux(
    isUnsign,
    Mux(
      isInvert,
      shsrc >> shamt,
      (shsrc << shamt)(XLEN - 1, 0)
    ),
    (shsrc.asSInt >> shamt).asUInt
  )

  // ==== Choose Logic ====
  val res = MuxLookup(ALUCtrl.getEncoded(ctrl), adderRes)(
    Seq(
      ALUCtrl.getEncoded(ALUCtrl.sll) -> shout,
      ALUCtrl.getEncoded(ALUCtrl.slt) -> ZeroExt(
        Mux(isUnsign, sltu, slt),
        XLEN
      ), // 對 Bool 值進行0拓展
      ALUCtrl.getEncoded(ALUCtrl.or) -> orRes,
      ALUCtrl.getEncoded(ALUCtrl.and) -> andRes,
      ALUCtrl.getEncoded(ALUCtrl.nor) -> norRes,
      ALUCtrl.getEncoded(ALUCtrl.xor) -> xorRes
    )
  )

  io.out.bits := res
  io.in.ready := io.out.ready
  io.out.valid := valid

  // ==== Log ====
  if (p.Log.LogALU) {
    Debug(
      io.in.fire,
      "[In  Fire] Ctrl %b SrcA 0x%x, SrcB 0x%x\n",
      ctrl,
      srcA,
      srcB
    )
    Debug(
      io.out.fire,
      "[Out Fire] Out 0x%x\n",
      io.out.bits
    )
  }
}
