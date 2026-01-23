package defs

private[defs] object RISCVDefs {
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
  private val MMIOBase = 0x00000000a0000000L
  private val MMIOSize = 0x0000000010000000L
  private val InDevBase = 0x30000000L
  private val InDevSize = 0x10000000L

  def mmio = List(
    (InDevBase, InDevSize),
    (MMIOBase, MMIOSize)
  )
}
