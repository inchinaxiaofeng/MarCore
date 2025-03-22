//package tlb
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import utils._
//import module.cache.{CacheBus, CacheBusReqBundle}
//import top.Settings
//
//trait HasTLBIO extends HasMarCoreParameter with HasTlbConst with HasCSRConst {
//	class TLBIO extends Bundle {
//		val in = Flipped(new CacheBus(userBits = userBits, addrBits = VAddrBits))
//		val out = new CacheBus(userBits = userBits)
//		
//		val mem = new CacheBus()
//		val flush =	Input(Bool())
//		val csrMMU = new MMUIO
//		val cacheEmpty = Input(Bool())
//		val ipf = Output(Bool())
//	}
//	val io = IO(new TLBIO)
//}
//
//// Translation Lookaside Buffer Memory Data
//class EmbeddedTLBMD(implicit val tlbConfig: TLBConfig) extends TlbModule {
//}
//
//class EmbeddedTLB(implicit val tlbConfig: TLBConfig) extends TlbModule with HasTLBIO {
//	// tlb exec
//	val tlbExec = Module(new EmbeddedTLBExec)
//	val tlbEmpty = Module(new EmbeddedTLBEmpty)
//	val 
//}
//
//class EmbeddedTLBExec(implicit val tlbConfig: TLBConfig) extends TlbModule with {
//	val io = IO(new Bundle {
//		val in = Flipped(Decoupled(new CacheBusReqBundle(userBits = userBits, addrBits = VAddrBits)))
//		val out = Decoupled(new CacheBusReqBundle(userBits = userBits))
//
//		val md = Input(Vec(Ways, UInt(tlbLen.W)))
//		val mdWrite = new TLBMDWriteBundle(IndexBits = IndexBits, Ways = Ways, tlbLen = tlbLen)
//		val mdReady = Input(Bool())
//
//		val mem = new CacheBus()
//		val flush = Input(Bool())
//		val satp = Input(UInt(XLEN.W))
//		val pf = new MMUIO
//		val ipf = Output(Bool())
//		val isFinish = Output(Bool())
//	})
//
//	val md = io.md
//
//	// lazy renaming
//	val req = io.in.bits
//	val vpn = req.addr.asTypeOf(vaBundle2).vpn.asTypeOf(vpnBundle)
//	val pf = io.pf
//	val satp = io.satp.asTypeOf(satpBundle)
//	val ifecth = if (tlbname == "itlb") true.B else false.B
//
//	pf.loadPF := false.B
//	pf.storePF := false.B
//	pf.addr := req.addr
//	
//	// check hit or miss
//	val hitVec =  VecInit(md.map(m => m.asTypeOf(tlbBundle).flag.asTypeOf(flagBundle).v && (m.asTypeOf(tlbBundle).asid === satp.asid) && MaskEQ(m.asTypeOf(tlbBundle).mask, m.asTypeOf(tlbBundle).vpn, vpn.asUInt))).asUInt
//	val hit = io.in.valid && hitVec.orR
//	val miss = io.in.valid && !hitVec.orR
//
//	val victimWaymask = if (Ways > 1) (1.U << LFSR64()(log2Up(Ways)-1, 0)) else "b1".U
//	val waymask = Mux(hit, hitVec, victimWaymask)
//
//	val loadPF = WireInit(false.B)
//	val storePF = WireInit(false.B)
//
//	// hit
//	val hitMeta = Mux1H(waymask, md).asTypeOf(tlbBundle2).meta.asTypeOf(metaBundle)
//	val hitData = Mux1H(waymask, md).asTypeOf(tlbBundle2).data.asTypeOf(dataBundle)
//	val hitFlag = hitMeta.flag.asTypeOf(flagBundle)
//	val hitMask = hitMeta.mask
//	// hit write back pte.flag
//	val hitinstrPF = WireInit(false.B)
//	val hitWB = hit && (!hitFlag.a || !hitFlag.d && req.isWrite()) && !hitinstrPF && !(load)
//}
//
//class EmbeddedTLBEmpty(implicit val tlbConfig: TLBConfig) extends TlbModule {
//	val io = IO(new Bundle {
//		val in = Flipped(Decoupled(new CacheBusReqBundle(userBits = userBits)))
//		val out = Decoupled(new CacheBusReqBundle(userBits = userBits))
//	})
//
//	io.out <> io.in
//}
//
//class EmbeddedTLB_fake(implicit val tlbConfig: TLBConfig) extends TlbModule with HasTLBIO {
//	io.mem <> DontCare
//	io.out <> io.in
//	io.csrMMU.loadPF := false.B
//	io.csrMMU.storePF := false.B
//	io.csrMMU.addr := io.in.req.bits.addr
//	io.ipf := false.B
//}
//
//object EmbeddedTLB {
//	def apply(in: CacheBus, mem: CacheBus, flush: Bool, csrMMU: MMUIO, enable: Boolean = true)(implicit tlbConfig: TLBConfig) = {
//		val tlb = if (enable) {
//			Module(new EmbeddedTLB)
//		} else {
//			Module(new EmbeddedTLB_fake)
//		}
//		tlb.io.in <> in
//		tlb.io.mem <> mem
//		tlb.io.flush := flush
//		tlb.io.csrMMU <> csrMMU
//		tlb
//	}
//}
