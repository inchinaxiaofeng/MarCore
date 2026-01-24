# `core.isa.instr`

## 目录简介

这个目录下存放 `RISC-V` 架构的指令定义, 及其对应的操作类型, 模块类型与模块操作码.

## 目录状态

活跃

## 设计原则

### 1. 设计哲学 (Design Philosophy)

本包定义了 `MarCore` 处理器中 "主流水线 (`Main Pipeline`)" 的通信标准。
我们遵循 "骨干与末梢分离" 的设计原则:

* `Pipeline` 包：定义贯穿处理器各个阶段(Fetch ↔ Decode ↔ Execute ↔ Commit)的通用骨干协议.
它是 CPU 的"大动脉".
* `Domain` 包：定义特定功能单元内部或点对点的私有协议(如 `core.uarch.branch` 或 `core.uarch.fu`).

### 2. 准入标准 (Inclusion Criteria)

一个 Bundle 只有满足以下 "流体属性" 才能进入本包:

* ✅ 跨阶段流动 (Cross-Stage Flow)
  数据需要在流水线的 3 个或更多阶段之间传递.
  典型代表: `CtrlFlowIO` (承载指令信息，从取指一直流到写回).
* ✅ 全局控制 (Global Control)
  信号具有"广播"性质，需要同时影响前端和后端多个模块.
  典型代表: `RedirectIO` (分支预测错误，全流水线冲刷), `ExceptionVec`.
* ✅ 标准级间握手 (Inter-Stage Handshake)
  定义了主要子系统(Frontend ↔ Backend)之间的交接契约.
  典型代表: `DispatchIO` (发射接口)、`CommitIO` (提交接口).

### 3. 拒绝标准 (Exclusion Criteria)

以下内容属于 "末梢细节", 请移步各自的专用包:

* ❌ 模块私有反馈: 仅在两个特定模块间闭环, 不影响全局.
  Go to: `core.uarch.branch` (e.g., `PredInfo` / `BPUUpdate`).
* ❌ 物理端口定义: 仅为了模块连线方便而定义的物理 `IO` 列表.
  Go to: 模块所在包或 `core.uarch.io`.
* ❌ 具体操作码定义: 常量定义.
  Go to: `core.uarch.fu` (e.g., `ALUOp`, `FuType`).

## 已知问题

## 可能问题

## 保留问题

## 单元测试情况

依赖整体测试.

## 开发建议

支持64Bit和32Bit, 并展开测试.
