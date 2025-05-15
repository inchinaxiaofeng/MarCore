package core.cache

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

import defs._
import utils._
import top.Settings
import bus.cacheBus._
import bus.debugBus.{DebugBus}
import module.cache._
import config.BaseConfig
import config.CacheReplacePolicy
import scala.annotation.meta.param

// ==== 配置与常数 ====

/** 可修改定义信息(在生成的时候请选定)
  *
  * @param ro
  *   ReadOnly, 当是ICache时选定.
  * @param name
  *   指定Log时的模块命名
  * @param cacheLevel
  *   指定当前Cache是Ln
  *   - 目前仅支持L1
  * @param userBits
  *   用户自定义接口
  * @param idBits
  *   用户自定义接口
  * @param cacheSize
  *   Cache 大小, 单位是 Kbytes
  * @param ways
  *   Cache 配置路数
  * @param lineSize
  *   Cache 行大小, 单位 byte
  * @param beatSize
  *   传输拍大小, 当对更低Cache或外部传递数据时, 每一拍最大传输数据量, byte为单位
  */
case class CacheConfig(
    ro: Boolean = false,
    name: String = "cache",
    cacheLevel: Int = 1,
    userBits: Int = 0,
    idBits: Int = 0,
    cacheSize: Int = 2, // Kbytes
    ways: Int = 4,
    lineSize: Int = 32, // byte
    beatSize: Int = 8 // byte Transfer width
)

/** Cache 常量计算与规定
  */
private[cache] trait HasCacheConst {
  implicit val cacheConfig: CacheConfig

  val PAddrBits: Int
  val XLEN: Int
  val cacheName = cacheConfig.name
  val userBits = cacheConfig.userBits
  val idBits = cacheConfig.idBits

  val ro = cacheConfig.ro // Read Only
  val hasCoh = !ro
  val hasCohInt = (if (hasCoh) 1 else 0)

  val cacheLevel = cacheConfig.cacheLevel
  val CacheSize = cacheConfig.cacheSize
  val Ways = cacheConfig.ways
  val LineSize = cacheConfig.lineSize // byte
  val LineBeats =
    LineSize / cacheConfig.beatSize // Data Width 64, the beats of trans whole line
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
  def getDataIdx(addr: UInt) =
    Cat(addr.asTypeOf(addrBundle).index, addr.asTypeOf(addrBundle).wordIndex)

  def isSameWord(a1: UInt, a2: UInt) = ((a1 >> 2) === (a2 >> 2))
  def isSetConflict(a1: UInt, a2: UInt) =
    (a1.asTypeOf(addrBundle).index === a2.asTypeOf(addrBundle).index)

}

// ==== 公共接口定义 ====

/** Cache 的对外公共接口
  *
  * @param cacheConfig
  */
class CacheIO(implicit val cacheConfig: CacheConfig) extends CacheBundle {
  val in = Flipped(new CacheBus(userBits = userBits, idBits = idBits))
  val flush = Input(UInt(2.W))
  val mem = new CacheBus()
  val mmio = new CacheBus()
  val empty = Output(Bool())
}

/** 引入对外公共接口
  */
trait HasCacheIO {
  implicit val cacheConfig: CacheConfig
  val io = IO(new CacheIO)
}

// ==== 抽象类定义 ====

/** 用于给Cache内部模块提供定义
  *
  * @param cacheConfig
  */
private[cache] abstract class CacheStageModule(implicit
    cacheConfig: CacheConfig
) extends MarCoreModule
    with HasCacheConst

/** 规范Cache对外包定义
  * @param cacheConfig
  */
private[cache] abstract class CacheBundle(implicit cacheConfig: CacheConfig)
    extends MarCoreBundle
    with HasCacheConst

/** 规范Cache对外模块定义
  *
  * @param cacheConfig
  */
private[cache] abstract class CacheModule(implicit cacheConfig: CacheConfig)
    extends MarCoreModule
    with HasCacheConst
    with HasCacheIO

// ==== Cache Line Defs ====

/** Cache Line Meta 信息
  */
private[cache] class MetaBundle(implicit val cacheConfig: CacheConfig)
    extends CacheBundle {
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

/** Cache Line 信息
  */
private[cache] class DataBundle(implicit val cacheConfig: CacheConfig)
    extends CacheBundle {
  val data = Output(UInt(DataBits.W))

  def apply(data: UInt) = {
    this.data := data
    this
  }
}

// ==== Gen ====

/** 伴生对象, 视作工厂单例
  */
object Cache {
  def apply(
      in: CacheBus,
      mmio: Seq[CacheBus],
      flush: UInt,
      empty: Bool,
      enable: Boolean = true
  )(implicit cacheConfig: CacheConfig) = {
    val cache = BaseConfig.cache match {
      case CacheReplacePolicy.NONE => Module(new NoneCache)
      case CacheReplacePolicy.RAND => Module(new RANDCache)
      case other =>
        throw new IllegalArgumentException(
          s"Unknown or unsupport cache policy: $other"
        )
    }
    cache.io.flush := flush
    cache.io.in <> in
    mmio(0) <> cache.io.mmio
    empty := cache.io.empty
    cache.io.mem
  }
}
