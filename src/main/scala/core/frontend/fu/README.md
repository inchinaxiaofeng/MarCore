# `core.isa.instr`

## 目录简介

### 复杂代码解析

#### `Decoder.scala` 下

假设你的 `NoSpecList` (禁止乱序执行的单元列表)是这样的:

```Scala
val NoSpecList = List(FuType.csr, FuType.mou) // 假设包含 CSR 和 MOU
```

当你执行这句话时：

```scala
NoSpecList.map(j => io.out.bits.ctrl.fuType === j).reduce(_ || _)
```

* Step 1: `map (生成比较电路)`:
  Scala 会遍历 `NoSpecList` 里的每一个元素 j，并把它转换成一个硬件比较结果 (Bool)。
  * 第一次：io.out.bits.ctrl.fuType === FuType.csr
  * 第二次：io.out.bits.ctrl.fuType === FuType.mou

此时，你得到了一个由硬件信号组成的列表：`List(Bool1, Bool2)`。

* Step 2: `reduce(_||_) (生成 OR 门树)`:
  reduce 会把列表里的元素用你指定的逻辑(这里是 ||，即逻辑或)两两捏合起来.
  * 它等效于: `Bool1 || Bool2`

最终生成的硬件电路：

```scala
// Verilog 等效逻辑
assign noSpecExec = (fuType == CSR) | (fuType == MOU);
```

### `NoSpec`, `Block`

| 特性 | `NoSpec` (非投机) | Block (阻塞/序列化) | 单发射约束 (你的`LSU`场景) |
| :--- | :--- | :--- | :--- |
| **关注点** | **自身安全** (不被前面坑) | **环境安全** (不坑后面) | **资源冲突** (端口不够) |
| **典型指令** | `MMIO Load/Store` | `FENCE, CSR Write, ERET` | 普通 `Load/Store` |
| **对前面的指令** | 必须等它们 Commit/无异常 | 无所谓 | 无所谓 |
| **对后面的指令** | 无所谓 (可以继续取指) | **必须停下来** (停止取指/发射) | **这一拍不能发** (下一拍再发) |
| **流水线动作** | 停在 EX/Commit 直到安全 | **清空流水线 (Drain)** | **部分发射 (Partial Issue)** |

## 目录状态

活跃

## 设计原则

## 已知问题

## 可能问题

## 保留问题

## 单元测试情况

## 开发建议
