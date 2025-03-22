package module.cache

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import top.Settings
import bus.cacheBus._
import module.cache.chiplab._
import bus.debugBus.{DebugBus}
import module.cache._

case class CacheConfig (
	ro: Boolean = false,
	name: String = "cache",
	swapPolicy: String = "SRRIP",
	cacheLevel: Int = 1,
	userBits: Int = 0,
	idBits: Int = 0,

	cacheSize: Int = 2, // Kbytes
	ways: Int = 4,
	lineSize: Int = 32, // byte
	beatSize: Int = 8, // byte Transfer width
)

trait HasCacheConst {
	implicit val cacheConfig: CacheConfig

	val PAddrBits: Int
	val XLEN: Int
	val cacheName = cacheConfig.name
	val userBits = cacheConfig.userBits
	val idBits = cacheConfig.idBits

	val ro = cacheConfig.ro // Read Only
	val hasCoh = !ro
	val hasCohInt = (if (hasCoh) 1 else 0)
	val hasPrefetch = cacheName == "l2cache"

	val cacheSwapPolicy = cacheConfig.swapPolicy

	val cacheLevel = cacheConfig.cacheLevel
	val CacheSize = cacheConfig.cacheSize
	val Ways = cacheConfig.ways
	val LineSize = cacheConfig.lineSize // byte
	val LineBeats = LineSize / cacheConfig.beatSize // Data Width 64, the beats of trans whole line
	val Sets = CacheSize * 1024 / LineSize / Ways
	val OffsetBits = log2Up(LineSize) // 5
	val IndexBits = log2Up(Sets) // 3
	val WordIndexBits = log2Up(LineBeats) // 2
	val TagBits = PAddrBits - OffsetBits - IndexBits // 56

	val debug = false

	def addrBundle = new Bundle {
		val tag = UInt(TagBits.W) // 56
		val index = UInt(IndexBits.W) // 3
		val wordIndex = UInt(WordIndexBits.W) // 2 bits
		val byteOffset = UInt((if (XLEN == 64) 3 else 2).W) // 3 bits
	}

//	def CacheMetaArrayReadBus()  = new SRAMReadBus(new MetaBundle, set = Sets, way = Ways)
//	def CacheMetaArrayWriteBus() = new SRAMWriteBus(new MetaBundle, set = Sets, way = Ways)
//	def CacheDataArrayReadBus()  = new SRAMReadBus (new DataBundle, set = Sets * LineBeats, way = Ways)
//	def CacheDataArrayWriteBus() = new SRAMWriteBus(new DataBundle, set = Sets * LineBeats, way = Ways)

	def getMetaIdx(addr: UInt) = addr.asTypeOf(addrBundle).index
	def getDataIdx(addr: UInt) = Cat(addr.asTypeOf(addrBundle).index, addr.asTypeOf(addrBundle).wordIndex)

	def isSameWord(a1: UInt, a2: UInt) = ((a1 >> 2) === (a2 >> 2))
	def isSetConflict(a1: UInt, a2: UInt) = (a1.asTypeOf(addrBundle).index === a2.asTypeOf(addrBundle).index)

}

abstract class CacheBundle(implicit cacheConfig: CacheConfig) extends Bundle with HasMarCoreParameter with HasCacheConst
abstract class CacheModule(implicit cacheConfig: CacheConfig) extends Module with HasMarCoreParameter with HasCacheConst

class MetaBundle(implicit val cacheConfig: CacheConfig) extends CacheBundle {
	val tag = Output(UInt(TagBits.W))
	val valid = Output(Bool())
	val dirty = Output(Bool())

	def apply(tag: UInt, valid: Bool, dirty: Bool) = {
		this.tag := tag
		this.valid := valid
		this.dirty := dirty
		this
	}
}

class DataBundle(implicit val cacheConfig: CacheConfig) extends CacheBundle {
	val data = Output(UInt(DataBits.W))

	def apply(data: UInt) = {
		this.data := data
		this
	}
}

class CacheIO(implicit val cacheConfig: CacheConfig) extends Bundle with HasMarCoreParameter with HasCacheConst {
	val in = Flipped(new CacheBus(userBits = userBits, idBits = idBits))
	val flush = Input(UInt(2.W))
	val mem = new CacheBus()
	val mmio = new CacheBus()
	val empty = Output(Bool())
}
trait HasCacheIO {
	implicit val cacheConfig: CacheConfig
}

class Cache_fake(implicit val cacheConfig: CacheConfig) extends CacheModule with HasCacheIO {
	implicit val moduleName: String = this.name
	val io = IO(new CacheIO)
	val s_idle :: s_memReq :: s_memResp :: s_mmioReq :: s_mmioResp :: s_wait_resp :: Nil = Enum(6)
	val state = RegInit(s_idle)

	val ismmio = AddressSpace.isMMIO(io.in.req.bits.addr)
	val ismmioRec = RegEnable(ismmio, io.in.req.fire)
	if (cacheConfig.name == "dacache") {
		BoringUtils.addSource(WireInit(ismmio), "lsumMMIO")
	}

	val needFlush = RegInit(false.B)
	when (io.flush(0) && (state =/= s_idle)) { needFlush := true.B }
	when (state === s_idle && needFlush) { needFlush := false.B }
	
	val alreadyOutFire = RegEnable(true.B, false.B, io.in.resp.fire)

	switch (state) {
		is (s_idle) {
			alreadyOutFire := false.B
			when (io.in.req.fire && !io.flush(0)) { state := Mux(ismmio, s_mmioReq, s_memReq)}
		}
		is (s_memReq)	{ when (io.mem.req.fire)  { state := s_memResp   } }
		is (s_memResp)	{ when (io.mem.resp.fire) { state := s_wait_resp } }
		is (s_mmioReq)	{ when (io.mmio.req.fire) { state := s_mmioResp  } }
		is (s_mmioResp)	{ when (io.mmio.resp.fire || alreadyOutFire) { state := s_wait_resp } }
		is (s_wait_resp){ when (io.in.resp.fire || needFlush || alreadyOutFire) { state := s_idle } }
	}

	val reqAddr	= RegEnable(io.in.req.bits.addr , io.in.req.fire)
	val reqLen	= RegEnable(io.in.req.bits.len  , io.in.req.fire)
	val reqSize	= RegEnable(io.in.req.bits.size , io.in.req.fire)
	val reqData	= RegEnable(io.in.req.bits.data , io.in.req.fire)
	val reqStrb	= RegEnable(io.in.req.bits.strb , io.in.req.fire)
	val reqLast	= RegEnable(io.in.req.bits.last , io.in.req.fire)
	val reqWrite= RegEnable(io.in.req.bits.write, io.in.req.fire)
	
	io.in.req.ready := (state === s_idle)
	io.in.resp.valid := (state === s_wait_resp) && (!needFlush)

	val mmioData	= RegEnable(io.mmio.resp.bits.data , io.mmio.resp.fire)
	val mmioLast	= RegEnable(io.mmio.resp.bits.last , io.mmio.resp.fire)
	val mmioWrite	= RegEnable(io.mmio.resp.bits.write, io.mmio.resp.fire)
	val memData		= RegEnable(io.mem.resp.bits.data , io.mem.resp.fire)
	val memLast		= RegEnable(io.mem.resp.bits.last , io.mem.resp.fire)
	val memWrite	= RegEnable(io.mem.resp.bits.write, io.mem.resp.fire)
	
	io.in.resp.bits.data  := Mux(ismmioRec, mmioData, memData)
	io.in.resp.bits.last  := Mux(ismmioRec, mmioLast, memLast)
	io.in.resp.bits.write := Mux(ismmioRec, mmioWrite, memWrite)

	val memUser = RegEnable(io.in.req.bits.user.getOrElse(0.U), io.in.req.fire)
	io.in.resp.bits.user.zip(if (userBits > 0) Some(memUser) else None).map { case (o, i) => o := i }

	io.mem.req.bits.apply(addr = reqAddr, size = reqSize, data = reqData, strb = reqStrb, last = reqLast, write = reqWrite, len = reqLen)
	io.mem.req.valid := (state === s_memReq)
	io.mem.resp.ready := true.B

	io.mmio.req.bits.apply(addr = reqAddr, size = reqSize, data = reqData, strb = reqStrb, last = reqLast, write = reqWrite, len = reqLen)
	io.mmio.req.valid := (state === s_mmioReq)
	io.mmio.resp.ready := true.B

	io.empty := false.B

	if (Settings.get("TraceCache")) {
		Debug(io.in.req.fire,	p"in.req: ${io.in.req.bits}\n")
		Debug(io.mem.req.fire,	p"mem.req: ${io.mem.req.bits}\n")
		Debug(io.mem.resp.fire,	p"mem.resp: ${io.mem.resp.bits}\n")
		Debug(io.in.resp.fire,	p"in.resp: ${io.in.resp.bits}\n")
	}
}

object Cache {
	def apply(in: CacheBus, mmio: Seq[CacheBus], flush: UInt, empty: Bool, enable: Boolean = true, swapPolicy: String = "SRRIP"/*, debugBus: DebugBus*/)(implicit cacheConfig: CacheConfig) = {
//		val cache = if (!enable) (Module(new Cache_fake))
//					else (swapPolicy match {
//						case "SRRIP"	=> (Module(new SRRIPCache))
//						case _			=> throw new IllegalArgumentException("Unsupported swap policy")
//					})
		val cache = Module(new LRUCache)
		cache.io.flush := flush
		cache.io.in <> in
		mmio(0) <> cache.io.mmio
		empty := cache.io.empty
//		cache.debugBus <> debugBus
		cache.io.mem
	}
}

object Cache_MIPS {
	def apply(in: CacheBus, mmio: Seq[CacheBus], flush: UInt, empty: Bool, enable: Boolean = true, swapPolicy: String = "SRRIP")(implicit cacheConfig: CacheConfig) = {
//		val cache = if (!enable) (Module(new Cache_fake))
//					else (swapPolicy match {
//						case "SRRIP"	=> (Module(new SRRIPCache))
//						case _			=> throw new IllegalArgumentException("Unsupported swap policy")
//					})
//		val cache = Module(new Cache_fake)
//		val cache = Module(new SRRIPCache)
		val cache = Module(new RANDCache)
		cache.io.flush := flush
		cache.io.in <> in
		mmio(0) <> cache.io.mmio
		empty := cache.io.empty
		cache.io.mem
	}
}
