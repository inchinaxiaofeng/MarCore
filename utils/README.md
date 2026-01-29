# sv2v Batch Converter (批量 SV 转 V 脚本)

这是一个轻量级的 Bash 脚本，用于递归地将 SystemVerilog (`.sv`) 文件批量转换为 Verilog (`.v`) 文件。它基于 [sv2v](https://github.com/zachjs/sv2v) 工具，主要用于解决 Yosys 等综合工具对 SystemVerilog 支持不完善的问题。

## 🛠 功能特点

* **保持目录结构**：自动在目标目录中重建源目录的文件夹结构。
* **批量处理**：一键转换整个项目。
* **宏定义支持**：**关键功能**。支持传入宏（例如 `SYNTHESIS`），可在转换过程中自动剔除带有 `ifndef SYNTHESIS` 保护的仿真代码（如 `$finish`、`$display`），生成纯净的可综合网表。

## 📦 前置依赖

必须安装 `sv2v` 并确保其在系统 PATH 中。

```bash
# 检查是否安装
sv2v --version

# 如果未安装 (Linux/macOS)
# 请参考 [https://github.com/zachjs/sv2v](https://github.com/zachjs/sv2v) 进行安装或下载预编译二进制文件
```

## 🚀 使用方法

### 基本语法

```bash
./sv2v_batch.sh <源目录> <输出目录> [可选:宏名称]
```

### 场景 1：生成综合用代码 (推荐)

如果你是为 Yosys、DC 或 Vivado 准备综合网表，请务必传入 `SYNTHESIS` 宏。这将移除所有不可综合的仿真逻辑。

```bash
# 示例：将 buildv 目录下的 SV 文件转换到 build_syn 目录，并定义 SYNTHESIS 宏
./sv2v_batch.sh ./buildv ./build_syn SYNTHESIS
```

效果：代码中的 `ifndef SYNTHESIS ...`endif 块将被移除，解决 `$finish` 报错问题。

### 场景 2：生成仿真用代码

如果你只是为了在不支持 SV 的仿真器（如某些老版本的 Verilator 或 Icarus Verilog）中运行，不需要移除仿真逻辑。

```bash
# 示例：不传入第三个参数
./sv2v_batch.sh ./buildv ./build_sim
```

## 📝 脚本权限

如果脚本无法执行，请先赋予执行权限：

```bash
chmod +x sv2v_batch.sh
```

## 📂 输出示例

假设源目录结构如下：

```Plaintext
src/
├── core/
│   └── Core.sv
└── utils/
    └── Arbiter.sv
```

运行脚本后，输出目录将生成：

```Plaintext
output/
├── core/
│   └── Core.v      <-- 已转换为 Verilog 2005 标准
└── utils/
    └── Arbiter.v
```

## 💡 常见问题 (Q&A)

Q: 为什么 `Yosys` 报错 `System task $finish outside initial block is unsupported?`
A: 这是因为转换后的 `Verilog` 代码保留了仿真语句. 请使用 场景 1 的方法, 在命令最后加上 `SYNTHESIS` 参数.

Q: 为什么需要这个脚本而不是直接用 `sv2v`?
A: `sv2v` 默认一次只处理给定的文件并输出到标准输出(`stdout`).
当项目文件众多且分布在不同子目录时, 手动管理非常麻烦.
这个脚本自动化了文件遍历和目录重建的过程.
