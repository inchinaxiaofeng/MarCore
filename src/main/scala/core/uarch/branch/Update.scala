package core.uarch.branch

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import core.isa.FuCtrl

/** 分支預測單元更新包.
  *
  * 定義了用於更新分支預測單元信息的格式.
  */
class BPUUpdate extends MarCoreBundle {

  /** 拉高此信號, 更新有效
    */
  val valid = Output(Bool())

  /** 更新時需要提供 PC
    */
  val pc = Output(UInt(VAddrBits.W))

  /** 表示是否預測錯誤
    *
    * 之所以將這個信號與Valid分開設計, 恰恰在於幾乎所有的分支預測都需要記錄過去歷史.
    *
    * 因此, valid 信號拉高代表更新歷史, 而歷史中是否預測錯誤交個這個信號提供.
    */
  val isMissPredict = Output(Bool())

  /** 實際的跳轉目標
    */
  val actualTarget = Output(UInt(VAddrBits.W))

  /** 實際的跳轉方向(跳轉或不跳轉)
    */
  val actualTaken = Output(Bool())

  /** fu控制信號
    *
    * 用於判斷指令類型,即區分 Branch, Direct(立即數跳轉), Call, Ret, Indirect(Reg跳轉)
    *
    * 這些分裂的區別, 由OneHot類型與簡單編碼組成.
    *
    * 在使用時請訪問方法而不是整個匹配, 這樣可以將FuCtrl中沒有用到的位優化.
    */
  val fuCtrl = Output(FuCtrl())

  /** 寫入BTB的類型
    */
  val btbType = Output(BTBType())
}
