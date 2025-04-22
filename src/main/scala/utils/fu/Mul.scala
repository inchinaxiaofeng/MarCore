package utils.fu

import chisel3._
import chisel3.util._
import utils._
import utils.fu.{C22, C32, C53}

/** `MulDivCtrl` 是一个控制信息打包结构，通常用于乘除法模块的配置信号。
  *   - `sign`：表示当前乘法是否为有符号运算。
  *   - `isW`：标记操作是否为 Word 类型（即 32-bit 而非全宽 64-bit）。
  *   - `isHi`：是否返回结果的高位部分（例如，在 hi/lo 分离的乘法中返回 hi 部分）。
  */
class MulDivCtrl extends Bundle {
  val sign = Bool()
  val isW = Bool()
  val isHi = Bool() // return hi bits of result ?
}

/** `ArrayMulDataModule` 实现了一个基于 Booth 编码与 CSA 压缩树的乘法器核心逻辑模块。
  *
  * 模块支持部分积生成、压缩、最终求和操作。其设计重点包括：
  *   - 使用 Booth 编码（2-bit 每次扫描，形成 3-bit 窗口）生成部分积。
  *   - 使用 Carry-Save Adder（CSA）树进行多位压缩，降低加法层数。
  *   - 使用可配置的时序寄存器（regEnables）控制流水级寄存逻辑。
  *
  * @param len
  *   操作数位宽
  *
  * @example
  *   {{{
  *       package example
  *
  *       import chisel3._
  *       import chisel3.util._
  *       import utils.fu.ArrayMulDataModule
  *
  *       class MulUnit(len: Int) extends Module {
  *         val io = IO(new Bundle {
  *           val a      = Input(UInt(len.W))
  *           val b      = Input(UInt(len.W))
  *           val valid  = Input(Bool())              // 啟動信號
  *           val result = Output(UInt((2 * len).W))  // 乘法結果
  *           val outValid = Output(Bool())           // 結果是否有效
  *         })
  *
  *         // --- internal pipeline control ---
  *         val pipeS0 = RegInit(false.B)
  *         val pipeS1 = RegInit(false.B)
  *         val pipeS2 = RegInit(false.B) // 最後輸出準備好
  *
  *         // pipeline push: 啟動時進入 pipeline
  *         when (io.valid) {
  *           pipeS0 := true.B
  *         }.elsewhen(pipeS0) {
  *           pipeS0 := false.B
  *         }
  *
  *         when (pipeS0) {
  *           pipeS1 := true.B
  *         }.elsewhen(pipeS1) {
  *           pipeS1 := false.B
  *         }
  *
  *         when (pipeS1) {
  *           pipeS2 := true.B
  *         }.elsewhen(pipeS2) {
  *           pipeS2 := false.B
  *         }
  *
  *         // instantiate multiplier core
  *         val mulCore = Module(new ArrayMulDataModule(len))
  *         mulCore.io.a := io.a
  *         mulCore.io.b := io.b
  *
  *         // 驅動內部 pipeline 使能（這裡你完全掌控）
  *         mulCore.io.regEnables(0) := pipeS0
  *         mulCore.io.regEnables(1) := pipeS1
  *
  *         // 接出結果
  *         io.result := mulCore.io.result
  *         io.outValid := pipeS2
  *       }
  *
  *   }}}
  *
  * ---
  *
  * # 模塊詳解
  *
  * 這個模塊的 pipeline 分為兩個註冊使能階段：
  *   - RegEnable(0)：對乘法部分積的每一列 column 做暫存，準備送入部分積壓縮（CSA tree）。
  *   - RegEnable(1)：在第一層 CSA 壓縮之後再註冊一輪，提升 timing。
  *
  * 最後是加法器輸出部分（sum + carry），這部分雖沒 pipeline，但是最後一個計算點。
  *
  * ## ⏱ Pipeline Latency（級數）
  *
  * 目前設計有：
  * | Stage | 描述                      | 是否寄存器 | 說明                         |
  * |:------|:------------------------|:------|:---------------------------|
  * | S0    | Booth 解碼 + 部分積生成        | ✅     | RegEnable(columns)         |
  * | S1    | 第一輪 CSA 壓縮              | ✅     | RegEnable(columns_next)    |
  * | S2    | 第二\~N輪 CSA 壓縮           | ❌     | recursive addAll()，目前沒有寄存器 |
  * | S3    | 終端 CPA（sum + carry）合併加法 | ❌     | 沒有 pipeline                |
  *
  * 所以 latency = 2 級 pipeline，但實際有 4 級邏輯。
  *
  * ## 🔧 Timing 路徑分析
  *
  * ### 路徑一：從輸入到部分積生成（S0）
  *
  *   - 路徑：io.a, io.b → Booth 解碼 → MuxLookup → SignExt/Shift → Cat → 填入 columns
  *   - 關鍵組件：
  *     - Booth 編碼器（3-bit slice 遍歷）
  *     - MuxLookup：大 Mux，根據 Booth 結果選擇 b, -b, b<<1, -b<<1
  *     - Cat 操作拼接
  *   - Tg 估計：Mux(4:1), 加上 shift，約為 1.52 個 Tg
  *
  * ### 路徑二：CSA Tree 一層（S1）
  *
  *   - 路徑：從 columns → 每列 addOneColumn → CSA2_2 / CSA3_2 / CSA5_3 模塊
  *   - 關鍵組件：
  *     - C22: XOR + AND
  *     - C32: 兩級 XOR, OR, AND
  *     - C53: 兩層 CSA3_2 相連
  *   - Tg 估計：
  *     - CSA2_2: 1 Tg（XOR+AND）
  *     - CSA3_2: 2\~2.5 Tg（XOR2 + OR）
  *     - CSA5_3: ≈ 4\~5 Tg（兩層 CSA3_2，算上 data reg）
  *
  * ### 路徑三：末端合併（S3）
  *
  *   - 路徑：sum + carry
  *   - 關鍵組件：
  *     - 寬位元加法器（最多 2×len bits）
  *   - Tg 估計：
  *     - 如果用 Ripple Carry Adder（假設），最大為 len 個 Tg
  *     - 實務應用會用 Carry-Lookahead 或前綴結構，加速至 log2(len)
  *     - 例如 len = 64，則 ≈ 6 Tg（prefix）
  *
  * ## 📊 每級最大 Tg 預估（假設 len=64）
  *
  * 總估計 delay（不 pipeline 情況下）：17\~19 Tg 而實際 pipeline 切到兩級，因此：
  *   - S0+S1 ≈ 6\~7 Tg（前兩級）
  *   - S2+S3 ≈ 10\~12 Tg（你可能會 timing fail）
  */
class ArrayMulDataModule(len: Int) extends Module {
  val io = IO(new Bundle() {

    /** 乘法器两个输入端 a, b */
    val a, b = Input(UInt(len.W))

    /** 两级寄存器使能信号。regEnables(0) 控制部分积寄存；regEnables(1) 控制中间层输出寄存。 */
    val regEnables = Input(Vec(2, Bool()))

    /** 最终输出乘法结果，位宽为 2*len */
    val result = Output(UInt((2 * len).W))
  })
  val (a, b) = (io.a, io.b)

  // --- Step 1: 生成 Booth 编码下的被乘数变换形式 ---
  val b_sext, bx2, neg_b, neg_bx2 = Wire(UInt((len + 1).W))
  b_sext := SignExt(b, len + 1) // 被乘数符号扩展
  bx2 := b_sext << 1 // 被乘数左移一位（乘2）
  neg_b := (~b_sext).asUInt // 被乘数按位取反（准备用于负值）
  neg_bx2 := neg_b << 1 // 左移以得到 -2b（负值乘2）

  /** `columns`：用于存储不同权重下的所有部分积位，按位分布 */
  val columns: Array[Seq[Bool]] = Array.fill(2 * len)(Seq())

  // 用于编码补偿的上一次 Booth 窗口值
  var last_x = WireInit(0.U(3.W))

  // --- Step 2: 基于 Booth 编码生成部分积，并填充至 columns ---
  for (i <- Range(0, len, 2)) {
    // Booth 窗口：每两个 bit 向前扩展 1 位形成 3-bit 编码
    val x =
      if (i == 0) Cat(a(1, 0), 0.U(1.W))
      else if (i + 1 == len) SignExt(a(i, i - 1), 3)
      else a(i + 1, i - 1)

    // 生成部分积：依据 Booth 编码 x 取不同版本的被乘数
    val pp_temp = MuxLookup(x, 0.U)(
      Seq(
        1.U -> b_sext,
        2.U -> b_sext,
        3.U -> bx2,
        4.U -> neg_bx2,
        5.U -> neg_b,
        6.U -> neg_b
      )
    )
    val s = pp_temp(len) // 获取符号位
    val t = MuxLookup(last_x, 0.U(2.W))( // 上一个 Booth 编码对应的补偿位
      Seq(
        4.U -> 2.U(2.W),
        5.U -> 1.U(2.W),
        6.U -> 1.U(2.W)
      )
    )
    last_x = x // 更新 last_x，供下一次迭代使用
    /** 构造带权部分积 pp 与其起始权重 */
    val (pp, weight) = i match {
      case 0 =>
        (Cat(~s, s, s, pp_temp), 0)
      case n if (n == len - 1) || (n == len - 2) =>
        (Cat(~s, pp_temp, t), i - 2)
      case _ =>
        (Cat(1.U(1.W), ~s, pp_temp, t), i - 2)
    }
    // 将部分积的每个位加入到对应权重列中
    for (j <- columns.indices) {
      if (j >= weight && j < (weight + pp.getWidth)) {
        columns(j) = columns(j) :+ pp(j - weight)
      }
    }
  }

  /** `addOneColumn`：对某一列中的 bits 和传入的 cin 进行压缩。
    *
    * 返回三部分结果：
    *   - sum：压缩结果
    *   - cout1 / cout2：两个级别的进位输出（用于下一列合并）
    */
  def addOneColumn(
      col: Seq[Bool],
      cin: Seq[Bool]
  ): (Seq[Bool], Seq[Bool], Seq[Bool]) = {
    // 逻辑分支根据输入 bit 数量决定使用的压缩器类型
    var sum = Seq[Bool]()
    var cout1 = Seq[Bool]()
    var cout2 = Seq[Bool]()
    col.size match {
      case 1 => // do nothing
        sum = col ++ cin
      case 2 =>
        val c22 = Module(new C22)
        c22.io.in := col
        sum = c22.io.out(0).asBool +: cin
        cout2 = Seq(c22.io.out(1).asBool)
      case 3 =>
        val c32 = Module(new C32)
        c32.io.in := col
        sum = c32.io.out(0).asBool +: cin
        cout2 = Seq(c32.io.out(1).asBool)
      case 4 =>
        val c53 = Module(new C53)
        for ((x, y) <- c53.io.in.take(4) zip col) {
          x := y
        }
        c53.io.in.last := (if (cin.nonEmpty) cin.head else 0.U)
        sum =
          Seq(c53.io.out(0).asBool) ++ (if (cin.nonEmpty) cin.drop(1) else Nil)
        cout1 = Seq(c53.io.out(1).asBool)
        cout2 = Seq(c53.io.out(2).asBool)
      case n =>
        val cin_1 = if (cin.nonEmpty) Seq(cin.head) else Nil
        val cin_2 = if (cin.nonEmpty) cin.drop(1) else Nil
        val (s_1, c_1_1, c_1_2) = addOneColumn(col take 4, cin_1)
        val (s_2, c_2_1, c_2_2) = addOneColumn(col drop 4, cin_2)
        sum = s_1 ++ s_2
        cout1 = c_1_1 ++ c_2_1
        cout2 = c_1_2 ++ c_2_2
    }
    (sum, cout1, cout2)
  }

  /** `max`：获取输入集合中的最大值 */
  def max(in: Iterable[Int]): Int = in.reduce((a, b) => if (a > b) a else b)

  /** `addAll`：递归式压缩所有列，直到每列最多两个 bit。
    *
    * 每层压缩结束后可选加寄存器（按需插入 pipeline 寄存），深度 4 层后停止。
    *
    * 返回 sum 与 carry，最终结果为 sum + carry。
    */
  def addAll(cols: Seq[Seq[Bool]], depth: Int): (UInt, UInt) = {
    if (max(cols.map(_.size)) <= 2) {
      val sum = Cat(cols.map(_(0)).reverse)
      var k = 0
      while (cols(k).size == 1) k = k + 1
      val carry = Cat(cols.drop(k).map(_(1)).reverse)
      (sum, Cat(carry, 0.U(k.W)))
    } else {
      val columns_next = Array.fill(2 * len)(Seq[Bool]())
      var cout1, cout2 = Seq[Bool]()
      for (i <- cols.indices) {
        val (s, c1, c2) = addOneColumn(cols(i), cout1)
        columns_next(i) = s ++ cout2
        cout1 = c1
        cout2 = c2
      }

      val needReg = depth == 4
      val toNextLayer =
        if (needReg)
          columns_next.map(_.map(x => RegEnable(x, io.regEnables(1))))
        else
          columns_next

      addAll(toNextLayer.toSeq, depth + 1)
    }
  }

  /** 初始部分积寄存（第一个 pipeline 层） */
  val columns_reg =
    columns.map(col => col.map(b => RegEnable(b, io.regEnables(0))))

  /** 最终压缩求和 */
  val (sum, carry) = addAll(cols = columns_reg.toSeq, depth = 0)

  /** 输出乘法结果 */
  io.result := sum + carry
}
