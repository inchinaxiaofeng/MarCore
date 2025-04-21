package utils

import chisel3._
import chisel3.util._

object LookupTree {

  /** `LookupTree` 基于一个键和键到数据值的映射构建一个多路选择器树。 它使用 `Mux1H`
    * 生成一个独热码多路选择器，根据匹配的键选择相应的数据值。 *
    *
    * ~log2(n)+log2(k)
    *
    * @note
    *   | 核心結構          | 延遲估算(Tg)         |
    *   |:--------------|:-----------------|
    *   | 比較器陣列 + Mux1H | ~log2(n)+log2(k) |
    *
    * @param key
    *   查找键 (UInt)。
    * @param mapping
    *   键值对的迭代器 (UInt, T)，其中 T 是 Chisel 数据类型。
    * @tparam T
    *   映射中值的 Chisel 数据类型。
    * @return
    *   根据匹配的键选择的数据值。
    *
    * @example
    *   {{{
    *     import chisel3._
    *     import chisel3.util._
    *
    *     class ExampleModule(data: UInt) extends Module {
    *       val io = IO(new Bundle {
    *         val sizeEncode = Input(UInt(2.W))
    *         val result = Output(UInt(32.W))
    *     })
    *
    *     io.result := LookupTree(io.sizeEncode, List(
    *       "b00".U -> Fill(8, data(7, 0)),
    *       "b01".U -> Fill(4, data(15, 0)),
    *       "b10".U -> Fill(2, data(31, 0)),
    *       "b11".U -> data
    *     ))}
    *   }}}
    */

  def apply[T <: Data](key: UInt, mapping: Iterable[(UInt, T)]): T =
    Mux1H(mapping.map(p => (p._1 === key, p._2))) // One Hot signals input
}

/** `LookupTreeDefault` 使用 `MuxLookup` 构建一个查找树，并提供一个默认值，以防没有找到匹配的键。
  *
  * @param key
  *   查找键 (UInt)。
  * @param default
  *   当没有找到匹配键时返回的默认值 (T)。
  * @param mapping
  *   一个键值对的迭代器 (UInt, T)，其中 T 是 Chisel Data 类型。
  * @tparam T
  *   映射中值的 Chisel Data 类型。
  * @return
  *   如果找到匹配的键，则返回相应的值；否则返回默认值。
  *
  * @example
  *   {{{
  *     import chisel3._
  *     import chisel3.util._
  *
  *     class ExampleModule extends Module {
  *     val io = IO(new Bundle {
  *     val select = Input(UInt(2.W))
  *     val out = Output(UInt(8.W))
  *     })
  *
  *     io.out := LookupTreeDefault(io.select, 0.U(8.W), List(
  *     "b00".U -> 10.U(8.W),
  *     "b01".U -> 20.U(8.W),
  *     "b10".U -> 30.U(8.W)
  *     ))
  *     }
  *   }}}
  */
object LookupTreeDefault {
  def apply[T <: Data](key: UInt, default: T, mapping: Iterable[(UInt, T)]): T =
    MuxLookup(key, default)(mapping.toSeq)
}
