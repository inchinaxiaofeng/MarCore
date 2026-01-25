package config

import chisel3._
import chisel3.util._

/** [自定义辅助类] AddressRegion 用来描述一段连续的物理地址空间，并提供生成硬件判断逻辑的能力。
  */
case class AddressRegion(base: BigInt, size: BigInt) {
  // 计算结束地址 (Exclusive, 不包含)
  def end: BigInt = base + size

  /** [硬件生成方法] contains 输入一个 Chisel 的 UInt 地址，输出一个 Bool 信号， 指示该地址是否落在这个区域内。
    */
  def contains(addr: UInt): Bool = {
    // 生成如下电路: (addr >= base) && (addr < base + size)
    addr >= base.U && addr < end.U
  }
}

case class SystemConfig(
    // 复位向量: PC 上电后的初始值
    resetVector: BigInt = 0x80000000L,

    // MMIO 区域列表：可能有多个离散的 IO 区域 (UART, SPI, PLIC...)
    // 这里的区域将被 LSU 识别为 "Uncached" (不可缓存)
    mmio: Seq[AddressRegion] = Seq(
      AddressRegion(0xa1000000L, 0x752ffL), // vmem
      AddressRegion(0xa0000100L, 0x8L), // vga ctrl
      AddressRegion(0xa0000048L, 0x8L), // rtc
      AddressRegion(0xa00003f8L, 0x8L), // serial
      AddressRegion(0xa0000060L, 0x3L), // keyboard
      ///
      AddressRegion(0x40000000L, 0x1000L), // flash
      AddressRegion(0x40002000L, 0x1000L), // dummy sdcard
      AddressRegion(0x40004000L, 0x1000L), // meipGen
      AddressRegion(0x40600000L, 0x10L), // uart
      AddressRegion(0x40003000L, 0x1000L) // dma
    )
) {

  /** [核心逻辑] 判断地址是否为 MMIO Core 只需要调用 p.sys.isMMIO(addr)，完全不用关心内部有几段区域
    */
  def isMMIO(addr: UInt): Bool = {
    if (mmio.isEmpty) false.B
    else mmio.map(_.contains(addr)).reduce(_ || _)
  }
}
