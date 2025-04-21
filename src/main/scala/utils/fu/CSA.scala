package utils.fu

import chisel3._
import chisel3.util._

/** `CarrySaveAdderMToN` 是一個抽象類，定義了 M 個輸入、N 個輸出的 Carry Save Adder 接口。
  *
  * @param m
  *   輸入向量的數量
  * @param n
  *   輸出向量的數量
  * @param len
  *   每個向量的位寬
  */
abstract class CarrySaveAdderMToN(m: Int, n: Int)(len: Int) extends Module {
  val io = IO(new Bundle() {
    val in = Input(Vec(m, UInt(len.W))) // M 個輸入，每個為 len-bit 的 UInt
    val out = Output(Vec(n, UInt(len.W))) // N 個輸出，每個為 len-bit 的 UInt
  })
}

/** 2:2 Carry Save Adder：將兩個相同長度的輸入轉換為 sum 和 carry 兩個輸出。
  *
  * 本質上是 bitwise 的半加器（half-adder），輸出 sum 與 carry。
  */
class CSA2_2(len: Int) extends CarrySaveAdderMToN(2, 2)(len) {
  val temp = Wire(Vec(len, UInt(2.W))) // 每一 bit 輸出為 2 位：{carry, sum}
  for ((t, i) <- temp.zipWithIndex) {
    val (a, b) = (io.in(0)(i), io.in(1)(i))
    val sum = a ^ b // 位加法：不帶進位
    val cout = a & b // 位進位
    t := Cat(cout, sum) // 合併為 2 位輸出
  }

  // 分別將所有 bit 的 sum 和 carry 拼接成完整向量
  io.out.zipWithIndex.foreach({ case (x, i) =>
    x := Cat(temp.reverse map (_(i))) // i=0: sum, i=1: carry
  })
}

/** 3:2 Carry Save Adder：三輸入兩輸出，全加器的向量版本。
  *
  * 對應於典型的 Full-Adder：
  *   - sum = a ^ b ^ cin
  *   - carry = a&b | b&cin | a&cin
  */
class CSA3_2(len: Int) extends CarrySaveAdderMToN(3, 2)(len) {
  val temp = Wire(Vec(len, UInt(2.W))) // 每一 bit 的 sum 與 carry

  for ((t, i) <- temp.zipWithIndex) {
    val (a, b, cin) = (io.in(0)(i), io.in(1)(i), io.in(2)(i))
    val a_xor_b = a ^ b
    val a_and_b = a & b
    val sum = a_xor_b ^ cin
    val cout = a_and_b | (a_xor_b & cin)
    t := Cat(cout, sum)
  }
  io.out.zipWithIndex.foreach({ case (x, i) =>
    x := Cat(temp.reverse map (_(i)))
  })
}

/** 5:3 Carry Save Adder：將 5 個輸入轉為 3 個輸出（2 個中間 sum，1 個最終 carry）。
  *
  * 通常在乘法器中使用，為了壓縮更多的輸入而設計的高階壓縮器。
  *
  * 實現方式：先使用一個 3:2 CSA，再使用另一個 3:2 CSA 處理剩餘的輸入。
  */
class CSA5_3(len: Int) extends CarrySaveAdderMToN(5, 3)(len) {
  val FAs = Array.fill(2)(Module(new CSA3_2(len)))

  // 第一層：壓縮前 3 個輸入
  FAs(0).io.in := io.in.take(3)

  // 第二層：壓縮前一層 sum，與剩餘的 2 個輸入
  FAs(1).io.in := VecInit(FAs(0).io.out(0), io.in(3), io.in(4))

  // 輸出為：最後一層 sum、第一層 carry、最後一層 carry
  io.out := VecInit(FAs(1).io.out(0), FAs(0).io.out(1), FAs(1).io.out(1))
}

/** 單位寬度版本的 CSA 模塊定義（常用於加法器列壓縮單元）
  *
  * 2:2 進位保存加法器
  */
class C22 extends CSA2_2(1)

/** 單位寬度版本的 CSA 模塊定義（常用於加法器列壓縮單元）
  *
  * 3:2 進位保存加法器
  */
class C32 extends CSA3_2(1)

/** 單位寬度版本的 CSA 模塊定義（常用於加法器列壓縮單元）
  *
  * 5:3 進位保存加法器
  */
class C53 extends CSA5_3(1)
