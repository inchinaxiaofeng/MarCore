package defs

private[defs] object LoongArchDefs {
  private val InstrBits = 32

  private val OpcodeBits = 7

  private val DoubleBits = 64
  private val WordBits = 32
  private val HalfBits = 16
  private val ByteBits = 8

  private val CSRHi = 31
  private val CSRLo = 20

  private val RS1Hi = 19
  private val RS1Lo = 15
  private val RS2Hi = 24
  private val RS2Lo = 20
  private val RDHi = 11
  private val RDLo = 7

  // MMIO区间
  private val ClintBase = 0x0200_0000L
  private val ClintSize = 0x0001_0000L

  private val ChipLinkBase = 0xc000_0000L
  private val ChipLinkSize = 0x4000_0000L // FIX: Should be 0x3000_0000L

  private val FlashBase = 0x3000_0000L
  private val FlashSize = 0x1000_0000L
  private val FlashMask = 0xffff_e000L

  private val SRAMBase = 0x0f00_0000L
  private val SRAMSize = 0x0000_2000L
  private val SRAMMask = 0xffff_e000L

  private val SDRAMBase = 0xa000_0000L
  private val SDRAMSize = 0x2000_0000L
  private val SDRAMMask = 0xe000_0000L

  private val PSRAMBase = 0x8000_0000L
  private val PSRAMSize = 0x2000_0000L
  private val PSRAMMask = 0xe000_0000L

  private val GPIOBase = 0x1000_2000L
  private val GPIOSize = 0x0000_0010L

  private val UART16550Base = 0x1000_0000L
  private val UART16550Size = 0x0000_1000L
  private val UART16550Mask = 0xffff_f000L

  private val SPIBase = 0x1000_1000L
  private val SPISize = 0x0000_2000L

  private val MROMBase = 0x2000_0000L
  private val MROMSize = 0x0000_2000L

  def mmio = List(
    (ClintBase, ClintSize),
    (ChipLinkBase, ChipLinkSize),
    (FlashBase, FlashSize),
    (SRAMBase, SRAMSize),
    (SDRAMBase, SDRAMSize),
    (PSRAMBase, PSRAMSize),
    (GPIOBase, GPIOSize),
    (UART16550Base, UART16550Size),
    (SPIBase, SPISize),
    (MROMBase, MROMSize)
  )
}
