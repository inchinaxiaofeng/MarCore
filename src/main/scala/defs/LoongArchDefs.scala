package defs

private[defs] object LoongArchDefs {
  val InstrBits = 32

  val OpcodeBits = 7

  val DoubleBits = 64
  val WordBits = 32
  val HalfBits = 16
  val ByteBits = 8

  val CSRHi = 31
  val CSRLo = 20

  val RS1Hi = 19
  val RS1Lo = 15
  val RS2Hi = 24
  val RS2Lo = 20
  val RDHi = 11
  val RDLo = 7

  // MMIO区间
  private val ClintBase = 0x0200_0000L
  private val ClintSize = 0x0000_ffffL

  private val ChipLinkBase = 0xc000_0000L
  private val ChipLinkSize = 0x2fff_ffffL

  private val FlashBase = 0x3000_0000L
  private val FlashSize = 0x0fff_ffffL
  private val FlashMask = 0xffff_e000L

  private val SRAMBase = 0x0f00_0000L
  private val SRAMSize = 0x0000_1fffL
  private val SRAMMask = 0xffff_e000L

  private val SDRAMBase = 0xa000_0000L
  private val SDRAMSize = 0x1fff_ffffL
  private val SDRAMMask = 0xe000_0000L

  private val PSRAMBase = 0x8000_0000L
  private val PSRAMSize = 0x1fff_ffffL
  private val PSRAMMask = 0xe000_0000L

  private val GPIOBase = 0x1000_2000L
  private val GPIOSize = 0x0000_000fL

  private val UART16550Base = 0x1000_0000L
  private val UART16550Size = 0x0000_0fffL
  private val UART16550Mask = 0xffff_f000L

  private val SPIBase = 0x1000_1000L
  private val SPISize = 0x0000_1fffL

  private val MROMBase = 0x2000_0000L
  private val MROMSize = 0x0000_0fffL

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
