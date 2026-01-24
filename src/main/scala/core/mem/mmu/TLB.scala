//package tlb
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import utils._
//import bus.cacheBus.{CacheBus, CacheBusReqBundle}
//import module.fu._
//import top.Settings
//
//sealed case class TLBConfig (
//	name: String = "tlb",
//	userBits: Int = 0,
//	totalEntry: Int = 4,
//	ways: Int = 4
//)
//
//sealed trait Sv39Const extends HasMarCoreParameter {
//	val Level = 3
//	val offLen = 12
//	val ppn0Len = 9
//	val ppn1Len = 9
//	val ppn2Len = PAddrBits - offLen - ppn0Len - ppn1Len // Should 29 maybe?
//	val ppnLen = ppn2Len + ppn1Len + ppn0Len
//	val vpn2Len = 9
//	val vpn1Len = 9
//	val vpn0Len = 9
//	val vpnLen = vpn2Len + vpn1Len + vpn0Len
//
//	val satpLen = XLEN
//	val satpModeLen = 4
//	val asidLen = 16
//	val flagLen = 8
//
//	val ptEntryLen = XLEN
//	val satpResLen = XLEN - ppnLen - satpModeLen - asidLen
//	val vaResLen = 25
//	val paResLen = 25
//	val pteResLen = XLEN - ppnLen - 2 - flagLen
//
//	def vaBundle = new Bundle {
//		val vpn2 = UInt(vpn2Len.W)
//		val vpn1 = UInt(vpn2Len.W)
//		val vpn0 = UInt(vpn0Len.W)
//		val off = UInt(offLen.W)
//	}
//
//	def vaBundle2 = new Bundle {
//		val vpn = UInt(vpnLen.W)
//		val off = UInt(offLen.W)
//	}
//
//	def vaBundle3 = new Bundle {
//		val vpn = UInt(vpnLen.W)
//		val off = UInt(offLen.W)
//	}
//
//	def vpnBundle = new Bundle {
//		val vpn2 = UInt(vpn2Len.W)
//		val vpn1 = UInt(vpn1Len.W)
//		val vpn0 = UInt(vpn0Len.W)
//	}
//
//	def paBundle = new Bundle {
//		val ppn2 = UInt(ppn2Len.W)
//		val ppn1 = UInt(ppn1Len.W)
//		val ppn0 = UInt(ppn0Len.W)
//		val off = UInt(offLen.W)
//	}
//
//	def paBundle2 = new Bundle {
//		val ppn = UInt(ppnLen.W)
//		val off = UInt(offLen.W)
//	}
//
//	def paddrApply(ppn: UInt, vpnn: UInt): UInt = {
//		Cat(Cat(ppn, vpnn), 0.U(3.W))
//	}
//
//	def pteBundle = new Bundle {
//		val reserved = UInt(pteResLen.W)
//		val ppn = UInt(ppnLen.W)
//		val rsw = UInt(2.W)
//		val flag = new Bundle {
//			val d = UInt(1.W)
//			val a = UInt(1.W)
//			val g = UInt(1.W)
//			val u = UInt(1.W)
//			val x = UInt(1.W)
//			val w = UInt(1.W)
//			val r = UInt(1.W)
//			val v = UInt(1.W)
//		}
//	}
//
//	def satpBundle = new Bundle {
//		val mode = UInt(satpModeLen.W)
//		val asid = UInt(asidLen.W)
//		val res = UInt(satpResLen.W)
//		val ppn = UInt(ppnLen.W)
//	}
//
//	def flagBundle = new Bundle {
//		val d = Bool()
//		val a = Bool()
//		val g = Bool()
//		val u = Bool()
//		val x = Bool()
//		val w = Bool()
//		val r = Bool()
//		val v = Bool()
//	}
//
//	def maskPaddr(ppn: UInt, vaddr: UInt, mask: UInt) = {
//		MaskData(vaddr, Cat(ppn, 0.U(offLen.W)), Cat(Fill(ppn2Len, 1.U(1.W)), mask, 0.U(offLen.W)))
//	}
//
//	def MaskEQ(mask: UInt, pattern: UInt, vpn: UInt) = {
//		(Cat("h1ff".U(vpn2Len.W), mask) & pattern) === (Cat("h1ff".U(vpn2Len.W), mask) & vpn)
//	}
//}
//
//trait HasTlbConst extends Sv39Const {
//	implicit val tlbConfig: TLBConfig
//
//	val AddrBits: Int
//	val PAddrBits: Int
//	val VAddrBits: Int
//	val XLEN: Int
//
//	val tlbname = tlbConfig.name
//	val userBits = tlbConfig.userBits
//
//	val maskLen = vpn0Len + vpn1Len // 18
//	val metaLen = vpnLen + asidLen + maskLen + flagLen
//	val dataLen = ppnLen + PAddrBits
//	val tlbLen = metaLen + dataLen
//	val Ways = tlbConfig.totalEntry
//	val TotalEntry = tlbConfig.totalEntry
//	val Sets = TotalEntry / Ways
//	val IndexBits = log2Up(Sets)
//	val TagBits = vpnLen - IndexBits
//
//	val debug = false
//
//	def vaddrTlbBundle = new Bundle {
//		val tag = UInt(TagBits.W)
//		val index = UInt(IndexBits.W)
//		val off = UInt(offLen.W)
//	}
//	
//	def metaBundle = new Bundle {
//		val vpn = UInt(vpnLen.W)
//		val asid = UInt(asidLen.W)
//		val mask = UInt(maskLen.W)
//		val flag = UInt(flagLen.W)
//	}
//
//	def dataBundle = new Bundle {
//		val ppn = UInt(ppnLen.W)
//		val pteaddr = UInt(PAddrBits.W)
//	}
//
//	def tlbBundle = new Bundle {
//		val vpn = UInt(vpnLen.W)
//		val asid = UInt(asidLen.W)
//		val mask = UInt(maskLen.W)
//		val flag = UInt(flagLen.W)
//		val ppn = UInt(ppnLen.W)
//		val pteaddr = UInt(PAddrBits.W)
//	}
//
//	def tlbBundle2 = new Bundle {
//		val meta = UInt(metaLen.W)
//		val data = UInt(dataLen.W)
//	}
//
//	def getIndex(vaddr: UInt): UInt = {
//		vaddr.asTypeOf(vaddrTlbBundle).index
//	}
//}
//
//abstract class TlbBundle(implicit tlbConfig: TLBConfig) extends MarCoreBundle with HasMarCoreParameter with HasTlbConst with Sv39Const
//abstract class TlbModule(implicit tlbConfig: TLBConfig) extends MarCoreModule with HasMarCoreParameter with HasTlbConst with Sv39Const with HasCSRConst
//
//sealed class TLBEmpty(implicit val tlbConfig: TLBConfig) extends TlbModule {
//	class TLBEmptyIO extends Bundle {
//		val in = Flipped(Decoupled(new CacheBusReqBundle(userBits = userBits)))
//		val out = Decoupled(new CacheBusReqBundle(userBits = userBits))
//	}
//	val io = IO(new TLBEmptyIO)
//
//	io.out <> io.in
//}
//
//object TLB {
//	def apply(in: CacheBus, mem: CacheBus, flush: Bool, csrMMU: MMUIO)(implicit tlbConfig: TLBConfig) = {
//		val tlb = Module(new TLBEmpty)
//		tlb.io.in <> in
//		tlb.io.mem <> mem
//		tlb.io.flush := flush
//		tlb.io.csrMMU <> csrMMU
//		tlb
//	}
//}
