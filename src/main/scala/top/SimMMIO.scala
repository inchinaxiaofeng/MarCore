package top

import chisel3._
import chisel3.util._

import defs._
import utils._
import bus.axi4._
import bus.cacheBus._
import device._

class SimMMIO extends Module {
  val io = IO(new Bundle {
    val rw = Flipped(new CacheBus)
    val meip = Output(Bool())
    val dma = new AXI4
    val uart = new UARTIO
  })

  val devAddrSpace = List(
      (0xa1000000L, 0x752ffL), // vmem
      (0xa0000100L, 0x8L), // vga ctrl
      (0xa0000048L, 0x8L), // rtc
      (0xa00003f8L, 0x8L), // serial
      (0xa0000060L, 0x3L), // keyboard
      ///
      (0x40000000L, 0x1000L), // flash
      (0x40002000L, 0x1000L), // dummy sdcard
      (0x40004000L, 0x1000L), // meipGen
      (0x40600000L, 0x10L), // uart
      (0x40003000L, 0x1000L) // dma
    )

  val xbar = Module(new CacheBusCrossbar1toN(devAddrSpace))
  xbar.io.in <> io.rw

  val uart = Module(new AXI4UART)
//  val vga = Module(new AXI4VGA(sim = true))
//  val flash = Module(new AXI4Flash)
//  val dma = Module(new AXI4DMA)
  uart.io.in <> xbar.io.out(0).toAXI4Lite
//  vga.io.in.fb <> xbar.io.out(1).toAXI4Lite
//  vga.io.in.ctrl <> xbar.io.out(2).toAXI4Lite
//  dma.io.in <> xbar.io.out(6).toAXI4Lite
//  io.dma <> dma.io.extra.get.dma
  io.meip := DontCare // meipGen.io.extra.get.meip
  uart.io.extra.get <> io.uart
//  vga.io.vga := DontCare
}
