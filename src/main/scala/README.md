# 目錄簡介

## 子目錄

以下目錄可能會造成困惑，這裏會進行詳細的區分

* `./config/`:
  * 符合要求即可修改。任何參數都通過枚舉的方式指定。
  * 我們可以認爲 Boolean 是一類特殊的枚舉
  * 被封裝好的選項，不依賴任何其他包中的內容。
  * 保證在任何沒有被阻止（例如有WIP標籤的、被註釋說明尚不支持的）的修改下，將不會發生功能性錯誤。
* `./settings/`
  * 主要由 `./config/` + `./defs/` 推演得到。
  * 未被推演得到的參數，符合要求即可修改。參數可以以任意方式被指定。在符合修改要求的情況下，將不會發生功能性錯誤。
  * 理想情況下，大多數參數將由 `./config/` 推演得到。
  * 提供並沒有被嚴格要求的不可變參數（就是那種沒有必要改，但是改了也不是不行的）
* `./defs/`
  * 其中的內容不可修改。參數可以以任意方式被指定。
  * 提供被ISA、手冊、或已經高度沉澱後的技術的，在任何情況下都不能修改的常數。
  * 提供跟MarCore實現相關的硬件參數、總線設計。部分內容將會通過Settings傳導到Defs中（通常用於定義硬件）
  * 提供標準 `MarCoreModule`, `MarCoreBundle`等。

三個目錄中，我們嚴格要求以下的單向依賴關係：

* `./config/` 不依賴任何其他包。
* `./settings/` 依賴 `config`
* `./defs/` 依賴 `./config/`, `./settings/`

三個目錄中，與RTL關係如下：

* `./config/` 不允許使用 `Chisel`
* `./defs/`, `./settings/` 允許使用 `Chisel`，但不允許實現任何功能性內容，或實例化任何代碼。
* `./defs/` 將可以提供 `MarCore` 會使用到的硬件接口定義、硬件標準化定義。

## 參數化配置接口

* `./config/`: 最常用的，完善封裝後的接口
* `./settings/`: 非面向用戶發佈的接口
* 在 `./defs/` 中的 `MarCoreConfig`，這個接口不能自行修改，這個是用於在實例化 MarCore 核心的時候使用的配置。
  * 目前僅僅要求在頂層封裝時，根據封裝層要求去配置，也就是實例化時配置。
  * 一般而言這類東西不會被稱作“參數化配置接口”，畢竟其不面向用戶開放，也不是一個實際上的可以自由修改的接口。
  * 但是考慮到其名字的特殊性，我在這裏特別提出，以避免混淆。

## 性能要求

| 類型 | 流水級邏輯深度(Tg) | 備註 |
| --- | --- | --- |
| 普通控制路徑 | 5~8 Tg | 控制單元, 跳轉預測等 |
| 加法器/比較器路徑 | 6~10 Tg | ALU, BRU 這種會佔點邏輯 |
| 乘法器, 複雜功能單元 | 10~20 Tg | 總體要求拆成多個 pipeline stage |
| Cache Access | 10~15 Tg | SRAM delay + 路由 |
| Load-Store Unit | 10~20 Tg | 包含 DTLB, 對齊, forward logic 等|

現代工藝下, 一個流水級大約是 6~12 Tg.
我們的核要求20 Tg的最低設計要求.

`MarCore` 提供的元語常見延遲參考

| 代碼(`Bool`表示一位的`UInt`) | 平均延遲(`Tg`) | 備註 |
| --- | --- | --- |
| `!UInt` | 0.1 - 0.3 | delay baseline, 很多文獻不把它單獨算作 `Tg` |
| `UInt ^ UInt` | 1 | `XOR2` 門 並行實例化, 極端快, 極端薄 |
| `UInt ^ UInt` | 1 | `XOR2` 門 並行實例化, 極端快, 極端薄 |
| `UInt =/= UInt` | 6 - 7 | XOR `1` + OR `log2(n)` + Not `0.1` |
| - | - | 下面爲 `MarCore` 提供的元語 |
| `Fill(XLEN, Bool)` | ~0 | 傳播延遲認爲0 |
| `Mux(Bool, UInt, UInt)` | 1 |  |
| `AdderGen(XLEN, UInt, UInt, Bool)` | 5 - 6 | `CLA~=log2(n)` |

Chisel 常用逻辑操作 & 门延迟估算表（以 Tg 为单位）

| 表达式 / 操作 | 描述 | RTL 结构 | 典型延迟 (Tg) |
| --- | --- | --- | --- |
| `!a` | 逻辑非 / 取反 | NOT gate | ~0.1–0.2 Tg |
| `a && b` | 两输入逻辑与 | 2-input AND | ~1 Tg |
| `a ^ b` | 异或 | XOR | ~1.2 Tg |
| `a === b (1位)` | 等于比较（1bit） | XNOR → AND | ~1.5 Tg |
| `a === b (n位)` | 等于比较（n-bit） | XOR → NOR tree | ~log₂(n) Tg |
| `a =/= b` | 不等比较 | XOR → OR tree | ~log₂(n) Tg |
| `a + b` | 加法器 | Ripple / CLA Adder | ~log₂(n) Tg |
| `a - b` | 减法器（同加法） | 加法 + 反码 + 1 | 同加法 |
| `Mux(sel, a, b)` | 2路选择器 | sel → a or b | ~1.5 Tg |
| `MuxLookup` | 多路选择器（case匹配） | 组合逻辑匹配树 | ~log₂(n) Tg |
| `.orR / .andR` | Reduction OR / AND | Tree结构 OR/AND | ~log₂(n) Tg |
| `a >= b, a < b 等` | 大小比较 | Subtractor + sign bit | ~log₂(n) Tg |
| `Cat(a, b)` | 连接位向量 | 无逻辑，纯拼接 | ~0 Tg |
| `a << b / a >> b` | 移位器 | Barrel shifter | ~log₂(n) Tg |
| `Fill(n, bit)` | 位填充 | 复制信号，无复杂逻辑 | ~0 Tg |
| - | - | - | MarCore 元語 |
| LookupTree()
