//package module.cache
//
//import chisel3._
//import chisel3.util._
//import chisel3.util.experimental.BoringUtils
//
//import defs._
//import utils._
//import top.Settings
//import bus.cacheBus._
//import module.cache._
//
//case class SmpCacheConfig (
//	smpName: String = "sampleCache",
//	smpUserBits: Int = 0,
//	smpIdBits: Int = 0,
//
//	smpCacheSize: Int = 2, // kb
//	smpWays: Int = 32,
//	smpLineSize: Int = 32, // same with l1 cache
//	beatSize: Int = 8
//)
//
//sealed class HawkeyeMetaBundle(ageBits: Int)(implicit cacheConfig: CacheConfig, implicit smpCacheConfig: SmpCacheConfig) extends MetaBundle {
//	val rrpv = Output(UInt(ageBits.W))
//	val pc_sign = Output(UInt(VAddrBits.W))
//
//	def apply(tag: UInt = 0.U, valid: Bool = false.B, dirty: Bool = false.B, rrpv: UInt = 0.U, pc_sign: UInt = 0.U) = {
//		super.apply(tag = tag, valid = valid, dirty = dirty)
//		this.rrpv := rrpv
//		this.pc_sign := pc_sign
//		this
//	}
//}
//
//sealed class HawkeyeSmpBundle(timerBits: Int)(implicit cacheConfig: CacheConfig) { // Sample Cache
//	val tag  = Output(UInt(13.W))    // Sample Tag
//	val lru  = Output(UInt())
//	val pc   = Output(UInt(VAddrBits.W))
//	val addr = Output(UInt(VAddrBits.W))
//	val prv_time = Output(UInt(timerBits.W))
//}
//
//sealed trait HasHawkeyeConst extends HasCacheConst {
//	implicit val smpCacheConfig: SampleCacheConfig
//
//	def smpAgeBits = 3
//	def smpMaxRRPV: UInt = ((1 << ageBits) - 1).U(ageBits.W)
//
//	val smpCacheName = smpCacheConfig.name
//	val smpUserBits = smpCacheConfig.smpUserBits
//	val smpIdBits = smpCacheConfig.smpIdBits
//
//	val smpCacheSize	= smpCacheConfig.smpCacheSize // kb
//	val smpWays			= smpCacheConfig.smpWays
//	val smpLineSize		= smpCacheConfig.smpLineSize
//	val smpSets			= smpCacheSize * 1024 / smpLineSize / smpWays
//
//	val smpOffsetBits		= log2Up(smpLineSize)
//	val smpIndexBits		= log2Up(smpSets)
//	val smpWordIndexBits	= log2Up(smpLineBeats)
//	val smpTagBits			= PAddrBits - smpOffsetBits - smpIndexBits
// 
//	def CacheMetaArrayReadBus () = new SRAMReadBus (new HawkeyeMetaBundle(ageBits = ageBits), set = Sets, way = Ways)
//	def CacheMetaArrayWriteBus() = new SRAMWriteBus(new HawkeyeMetaBundle(ageBits = ageBits), set = Sets, way = Ways)
//	def CacheMetaArrayFlushBus() = new SRAMFlushBus(new HawkeyeMetaBundle(ageBits = ageBits), way = Ways)
//	def CacheDataArrayReadBus () = new SRAMReadBus (new DataBundle, set = Sets * LineBeats, way = Ways)
//	def CacheDataArrayWriteBus() = new SRAMWriteBus(new DataBundle, set = Sets * LineBeats, way = Ways)
//
//	def SmpArrayReadBus () = new SRAMReadBus (new HawkeyeSmpBundle(timerBits = timerBits), set = SmpSets, way = SmpWays)
//	def SmpArrayWriteBus() = new SRAMWriteBus(new HawkeyeSmpBundle(timerBits = timerBits), set = SmpSets, way = SmpWays)
//
//	def smpAddrBundle = new Bundle {
//		val tag = UInt(TagBits.W)
//		val index = UInt(IndexBits.W)
//		val wordIndex = UInt(WordIndexBits.W)
//		val byteOffset = UInt((if (XLEN == 64) 3 else 2).W)
//	}
//	def getSmpIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
//}
//
//sealed class Stage1IO(implicit val cacheConfig: CacheConfig, implicit val smpCacheConfig: SmpCacheConfig) extends CacheBundle with HasCacheConst {
//	val req = new CacheBusReqBundle(userBits = userBits, idBits = idBits)
//}
//
//sealed class HawkeyeCacheStage1(implicit val cacheConfig: CacheConfig) extends CacheModule with HasHawkeyeConst {
//	implicit val moduleName: String = cacheName
//	class CacheStage1IO extends Bundle {
//		val in = Flipped(Decoupled(new CacheBusReqBundle(userBits = userBits, idBits = idBits)))
//		val out = Decoupled(new Stage1IO)
//		val metaReadBus = CacheMetaArrayReadBus()
//		val dataReadBus = CacheDataArrayReadBus()
//		val smpReadBus = SmpArrayReadBus()
//	}
//	val io = IO(new CacheStage1IO)
//
//	Debug(io.in.fire, "[L1$] Cache stage 1, addr in: %x, user: %x, id: %x\n", io.in.bits.addr, io.in.bits.user.getOrElse(3053.U), io.in.bits.id.getOrElse(3053.W))
//
//	val readBusValid = io.in.valid && io.out.ready
//	io.metaReadBus.apply(valid = readBusValid, setIdx = getMetaIdx(io.in.bits.addr))
//	io.dataReadBus.apply(valid = readBusValid, setIds = getDataIdx(io.in.bits.addr))
//	io.smpReadBus.apply(valid = readBusValid, setIdx = getSmpIdx(io.in.bits.addr))
//
//	io.out.bits.req := io.in.bits
//	io.out.valid	:= io.in.valid && io.metaReadBus.req.ready && io.dataReadBus.req.ready && io.smpReadBus.req.ready
//	io.in.ready 	:= (!io.in.valid || io.out.fire) && io.metaReadBus.req.ready && io.dataReadBus.req.ready && io.smpReadBus.req.ready
//}
//
//sealed class CacheStage2IO(implicit val cacheConfig: CacheConfig, implicit val smpCacheConfig: SmpCacheConfig) extends CacheBundle with HasHawkeyeConst {
//	val req = new CacheBusReqBundle(userBits = userBits, idBits = idBits)
//	val metas = Vec(Ways, )
//	
//}
//
//sealed class HawkeyeCacheStage2(implicit val cacheConfig: CacheConfig, implicit val smpCacheConfig: SampleCacheConfig) extends CacheModule with HasHawkeyeConst {
//	val 
//}