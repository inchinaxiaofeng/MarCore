// package core
//
// import chisel3._
// import circt.stage._
// import chisel3.util._
//
// import defs.{MarCoreConfig, MarCoreModule}
// import core.frontend.Frontend_embedded
// import core.backend.Backend_inorder
// import core.uarch.interfaces.DecodeIO
// import utils.PipelineVector2Connect
// import bus.cacheBus.CacheBus
// import utils.Info
//
// /** 执行核.
//   *
//   * @note
//   *   - CPU中实际执行运算的部分, 不包含复杂的存储子系统.
//   *   - 根据Intel微架构文档, 将负责调度和运算的逻辑统称为Execution Core.
//   */
// class ExecCore(implicit val p: MarCoreConfig) extends MarCoreModule {
//   implicit val moduleName: String = this.name
//   class ExecCoreIO extends Bundle {
//     val imem = new CacheBus(userBits = ICacheUserBundleWidth)
//     val dmem = new CacheBus
//   }
//   val io = IO(new ExecCoreIO)
//
//   val frontend = if (p.Core.EnableMultiIssue) {
//     Info("EnableMultiIssue not impl yet.")
//     Module(new Frontend_embedded())
//   } else {
//     Module(new Frontend_embedded())
//   }
//
//   val backend = if (p.Core.EnableOutOfOrderExec) {
//     Info("EnableOutOfOrderExec not impl yet.")
//     Module(new Backend_inorder())
//   } else {
//     Module(new Backend_inorder())
//   }
//
//   io.imem <> frontend.io.imem
//   io.dmem <> backend.io.dmem
//
//   PipelineVector2Connect(
//     new DecodeIO,
//     frontend.io.out(0),
//     frontend.io.out(1),
//     backend.io.in(0),
//     backend.io.in(1),
//     frontend.io.flushVec(1),
//     8
//   )
//
//   frontend.io.ipf := false.B
//
//   // redirect
//   frontend.io.redirect <> backend.io.redirect
//   frontend.io.bpuUpdate <> backend.io.bpuUpdate
//   backend.io.flush := frontend.io.flushVec(3, 2)
//
// }
