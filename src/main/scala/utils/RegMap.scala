package utils

import chisel3._
import chisel3.util._

import top.Settings

/** `RegMap` 对象用于管理寄存器映射，并生成读写寄存器的逻辑。 它支持寄存器的读写，并允许为写操作指定转换函数。
  *
  * @example
  *   {{{
  *     import chisel3._
  *     import chisel3.util._
  *
  *     class ExampleRegMap extends Module {
  *       val io = IO(new Bundle {
  *         val raddr = Input(UInt(8.W))
  *         val rdata = Output(UInt(32.W))
  *         val waddr = Input(UInt(8.W))
  *         val wen = Input(Bool())
  *         val wdata = Input(UInt(32.W))
  *         val wmask = Input(UInt(32.W))
  *       })
  *
  *       val reg0 = RegInit(0.U(32.W))
  *       val reg1 = RegInit(0.U(32.W))
  *       val reg2 = RegInit(0.U(32.W))
  *
  *       val mapping = Map(
  *         0x00 -> RegMap(0x00, reg0),
  *         0x04 -> RegMap(0x04, reg1, (x: UInt) => x + 1.U), // 写操作时加 1
  *         0x08 -> RegMap(0x08, reg2)
  *       )
  *
  *       RegMap.generate(mapping, io.raddr, io.rdata, io.waddr, io.wen, io.wdata, io.wmask)
  *     }
  *   }}}
  *
  * // 用法示例： // `RegMap.generate` 根据 `mapping` 生成读写寄存器的逻辑。 // `raddr`
  * 用于指定要读取的寄存器地址，`rdata` 返回读取的数据。 // `waddr`、`wen`、`wdata` 和 `wmask`
  * 用于指定要写入的寄存器地址、使能信号、数据和掩码。 // 可以通过 `wfn` 参数为写操作指定转换函数。
  */
object RegMap {

  /** 表示不可写的寄存器。
    */
  def Unwritable = null

  /** 创建一个寄存器映射条目。
    *
    * @param addr
    *   寄存器地址。
    * @param reg
    *   寄存器。
    * @param wfn
    *   写操作的转换函数 (UInt => UInt)。
    * @return
    *   寄存器映射条目 (addr, (reg, wfn))。
    */
  def apply(addr: Int, reg: UInt, wfn: UInt => UInt = (x => x)) =
    (addr, (reg, wfn)) // [key, (reg, function)]

  /** 生成读写寄存器的逻辑。
    *
    * @param mapping
    *   寄存器映射 (Map[Int, (UInt, UInt => UInt)])。
    * @param raddr
    *   读地址。
    * @param rdata
    *   读数据输出。
    * @param waddr
    *   写地址。
    * @param wen
    *   写使能信号。
    * @param wdata
    *   写数据。
    * @param wmask
    *   写掩码。
    */
  def generate(
      mapping: Map[Int, (UInt, UInt => UInt)],
      raddr: UInt,
      rdata: UInt,
      waddr: UInt,
      wen: Bool,
      wdata: UInt,
      wmask: UInt
  ): Unit = {
    val chiselMapping = mapping.map { case (a, (r, w)) => (a.U, r, w) }
    rdata := LookupTree(raddr, chiselMapping.map { case (a, r, w) => (a, r) })
    chiselMapping.map { case (a, r, w) =>
      if (w != null) when(wen && waddr === a) {
        r := w(MaskData(r, wdata, wmask))
      }
    }
  }

  /** 生成读写寄存器的逻辑（读写地址相同）。
    *
    * @param mapping
    *   寄存器映射 (Map[Int, (UInt, UInt => UInt)])。
    * @param addr
    *   读写地址。
    * @param rdata
    *   读数据输出。
    * @param wen
    *   写使能信号。
    * @param wdata
    *   写数据。
    * @param wmask
    *   写掩码。
    */
  def generate(
      mapping: Map[Int, (UInt, UInt => UInt)],
      addr: UInt,
      rdata: UInt,
      wen: Bool,
      wdata: UInt,
      wmask: UInt
  ): Unit = generate(mapping, addr, rdata, addr, wen, wdata, wmask)
} // 处理寄存器映射和相关逻辑

// MaskedRegMap 对象文档
/** `MaskedRegMap` 对象用于管理带掩码的寄存器映射，并生成读写寄存器的逻辑。 它支持寄存器的读写，并允许为读写操作指定掩码和转换函数。
  *
  * @example
  *   {{{
  *     import chisel3._
  *     import chisel3.util._
  *
  *     class ExampleMaskedRegMap extends Module {
  *       val io = IO(new Bundle {
  *         val raddr = Input(UInt(8.W))
  *         val rdata = Output(UInt(32.W))
  *         val waddr = Input(UInt(8.W))
  *         val wen = Input(Bool())
  *         val wdata = Input(UInt(32.W))
  *       })
  *
  *       val reg0 = RegInit(0.U(32.W))
  *       val reg1 = RegInit(0.U(32.W))
  *       val reg2 = RegInit(0.U(32.W))
  *
  *       val mapping = Map(
  *         0x00 -> MaskedRegMap(0x00, reg0),
  *         0x04 -> MaskedRegMap(0x04, reg1, wmask = 0x0F.U(32.W), (x: UInt) => x + 1.U), // 仅写入低 4 位，并加 1
  *         0x08 -> MaskedRegMap(0x08, reg2, rmask = 0x0F.U(32.W)) // 仅读取低 4 位
  *       )
  *
  *       MaskedRegMap.generate(mapping, io.raddr, io.rdata, io.waddr, io.wen, io.wdata)
  *     }
  *   }}}
  *
  * // 用法示例： // `MaskedRegMap.generate` 根据 `mapping` 生成读写寄存器的逻辑。 // `raddr`
  * 用于指定要读取的寄存器地址，`rdata` 返回读取的数据。 // `waddr`、`wen` 和 `wdata`
  * 用于指定要写入的寄存器地址、使能信号和数据。 // 可以通过 `wmask` 和 `rmask` 参数为读写操作指定掩码，通过 `wfn`
  * 参数为写操作指定转换函数。
  */
object MaskedRegMap {

  /** 表示不可写的寄存器。
    */
  def Unwritable = null

  /** 表示无副作用的转换函数。
    */
  def NoSideEffect: UInt => UInt = (x => x)

  /** 表示可写的掩码。
    */
  def WritableMask = Fill(if (Settings.get("IsRV32")) 32 else 64, true.B)

  /** 表示不可写的掩码。
    */
  def UnwritableMask = 0.U(if (Settings.get("IsRV32")) 32.W else 64.W)

  /** 创建一个带掩码的寄存器映射条目。
    *
    * @param addr
    *   寄存器地址。
    * @param reg
    *   寄存器。
    * @param wmask
    *   写掩码。
    * @param wfn
    *   写操作的转换函数 (UInt => UInt)。
    * @param rmask
    *   读掩码。
    * @return
    *   带掩码的寄存器映射条目 (addr, (reg, wmask, wfn, rmask))。
    */
  def apply(
      addr: Int,
      reg: UInt,
      wmask: UInt = WritableMask,
      wfn: UInt => UInt = (x => x),
      rmask: UInt = WritableMask
  ) = (addr, (reg, wmask, wfn, rmask))

  def generate(
      mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)],
      raddr: UInt,
      rdata: UInt,
      waddr: UInt,
      wen: Bool,
      wdata: UInt
  ): Unit = {
    val chiselMapping = mapping.map { case (a, (r, wm, w, rm)) =>
      (a.U, r, wm, w, rm)
    }
    rdata := LookupTree(
      raddr,
      chiselMapping.map { case (a, r, wm, w, rm) => (a, r & rm) }
    )
    chiselMapping.map { case (a, r, wm, w, rm) =>
      if (w != null && wm != UnwritableMask) when(wen && waddr === a) {
        r := w(MaskData(r, wdata, wm))
      }
    }
  }
  def isIllegalAddr(
      mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)],
      addr: UInt
  ): Bool = {
    val illegalAddr = Wire(Bool())
    val chiselMapping = mapping.map { case (a, (r, wm, w, rm)) =>
      (a.U, r, wm, w, rm)
    }
    illegalAddr := LookupTreeDefault(
      addr,
      true.B,
      chiselMapping.map { case (a, r, wm, w, rm) => (a, false.B) }
    )
    illegalAddr
  }
  def generate(
      mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)],
      addr: UInt,
      rdata: UInt,
      wen: Bool,
      wdata: UInt
  ): Unit = generate(mapping, addr, rdata, addr, wen, wdata)
}
