//package module.fu.loongarch
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import utils._
//import top.Settings
//
//object CSRCtrl {
//	def jmp  = "b000".U
//	def wrt  = "b001".U
//	def set  = "b010".U
//	def clr  = "b011".U
//	def wrti = "b101".U
//	def seti = "b110".U
//	def clri = "b111".U
//}
//
//trait HasCSRConst {
//	val CRMD		= 0x0		// 当前模式信息
//	val PRMD		= 0x1		// 例外前模式信息
//	val EUEN		= 0x2		// 扩展部件使能
//	val ECFG		= 0x4		// 例外配置
//	val ESTAT		= 0x5		// 例外状态
//	val ERA			= 0x6		// 例外返回地址
//	val BADV		= 0x7		// 出错虚地址
//	val EENTRY		= 0xc		// 例外入口地址
//	val TLBIDX		= 0x10		// TLB 索引
//	val TLBEHI		= 0x11		// TLB 表项高位
//	val TLBELO0		= 0x12		// TLB 表项低位 0
//	val TLBELO1		= 0x13		// TLB 表项低位 1
//	val ASID		= 0x18		// 地址空间标识符
//	val PGDL		= 0x19		// 低半地址空间全局目录基址
//	val PGDH		= 0x1A		// 高半地址空间全局目录基址
//	val PGD			= 0x1B		// 全局目录基址
//	val CPUID		= 0x20		// 处理器编号
//	val SAVE0		= 0x30		// 数据保存
//	val SAVE1		= 0x31		// 数据保存
//	val SAVE2		= 0x32		// 数据保存
//	val SAVE3		= 0x33		// 数据保存
//	val TID			= 0x40		// 定时器编号
//	val TCFG		= 0x41		// 定时器配置
//	val TVAL		= 0x42		// 定时器值
//	val TICLR		= 0x44		// 定时中断清除
//	val LLBCTL		= 0x60		// LLBit 控制
//	val TLBRENTRY	= 0x88		// TLB 重填例外入口地址
//	val CTAG		= 0x98		// 高速缓存标签
//	val DMW0		= 0x180		// 直接映射配置窗口
//	val DMW1		= 0x181		// 直接映射配置窗口
//
//	// Exception code table and exception code sub table
//	def INT		= (0x0.U , 0.U)	// 中断
//	def PIL		= (0x1.U , 0.U) // load 操作页无效例外
//	def PIS		= (0x2.U , 0.U) // store 操作页无效例外
//	def PIF		= (0x3.U , 0.U) // 取指操作页无效例外
//	def PME		= (0x4.U , 0.U) // 页修改例外
//	def PPI		= (0x7.U , 0.U) // 页特权等级不合规例外
//	def ADEF	= (0x8.U , 0.U) // 取指地址错例外
//	def ADEM	= (0x8.U , 1.U) // 访存指令地址错例外
//	def ALE		= (0x9.U , 0.U) // 地址非对齐例外
//	def SYS		= (0xB.U , 0.U) // 系统调用例外
//	def BRK		= (0xC.U , 0.U) // 断点例外
//	def INE		= (0xD.U , 0.U) // 指令不存在例外
//	def IPE		= (0xE.U , 0.U) // 指令特权等级错例外
//	def FPD		= (0xF.U , 0.U) // 浮点指令未使能例外
//	def FPE		= (0x12.U, 0.U) // 基础浮点指令例外
//	def TLBR	= (0x3F.U, 0.U) // TLB 重填例外
//
//	def IRQ_UEIP	= 0
//	def IRQ_SEIP	= 1
//	def IRQ_MEIP	= 3
//
//	def IRQ_UTIP	= 4
//	def IRQ_STIP	= 5
//	def IRQ_MTIP	= 7
//
//	def IRQ_USIP	= 8
//	def IRQ_SSIP	= 9
//	def IRQ_MSIP	= 11
//
//	val IntPriority = Seq(
//		IRQ_MEIP, IRQ_MSIP, IRQ_MTIP,
//		IRQ_SEIP, IRQ_SSIP, IRQ_STIP,
//		IRQ_UEIP, IRQ_USIP, IRQ_UTIP
//	)
//}
//
//trait HasExceptionNO {
//	def INT		= (0x0.U , 0.U)	// 中断
//	def PIL		= (0x1.U , 0.U) // load 操作页无效例外
//	def PIS		= (0x2.U , 0.U) // store 操作页无效例外
//	def PIF		= (0x3.U , 0.U) // 取指操作页无效例外
//	def PME		= (0x4.U , 0.U) // 页修改例外
//	def PPI		= (0x7.U , 0.U) // 页特权等级不合规例外
//	def ADEF	= (0x8.U , 0.U) // 取指地址错例外
//	def ADEM	= (0x8.U , 1.U) // 访存指令地址错例外
//	def ALE		= (0x9.U , 0.U) // 地址非对齐例外
//	def SYS		= (0xB.U , 0.U) // 系统调用例外
//	def BRK		= (0xC.U , 0.U) // 断点例外
//	def INE		= (0xD.U , 0.U) // 指令不存在例外
//	def IPE		= (0xE.U , 0.U) // 指令特权等级错例外
//	def FPD		= (0xF.U , 0.U) // 浮点指令未使能例外
//	def FPE		= (0x12.U, 0.U) // 基础浮点指令例外
//	def TLBR	= (0x3F.U, 0.U) // TLB 重填例外
//
//	val ExcPriority = Seq (
//		INT,
//		PIL,
//		PIS,
//		PIF,
//		PME,
//		PPI,
//		ADEF,
//		ADEM,
//		ALE,
//		SYS,
//		BRK,
//		INE,
//		IPE,
//		FPD,
//		FPE,
//		TLBR	
//	)
//}
//
//class CSRIO extends FuCtrlIO {
//	val cfIn = Flipped(new CtrlFlowIO)
//	val redirect = new RedirectIO
//	// for exception check
//	val instrValid = Input(Bool())
//	val isBackendException = Input(Bool())
//	// for differential testing
//	val intrNO = Output(UInt(XLEN.W))
////	val imemMMU = Flipped(new MMUIO)
////	val dmemMMU = Flipped(new MMUIO)
//	val wenFix = Output(Bool())
//	val csr = new RegsDiffIO(num = 4)
//}
//
//class CSR (implicit val p: MarCoreConfig) extends MarCoreModule with HasCSRConst {
//	implicit val moduleName: String = this.name
//	val io = IO(new CSRIO)
//
//	val (valid, srcA, srcB, ctrl) = (io.in.valid, io.in.bits.srcA, io.in.bits.srcB, io.in.bits.ctrl)
//	def access(valid: Bool, srcA: UInt, srcB: UInt, ctrl: UInt): UInt = {
//		this.valid := valid
//		this.srcA := srcA
//		this.srcB := srcB
//		this.ctrl := ctrl
//		io.out.bits
//	}
//
//	// CSR define
//	class Priv extends Bundle {
//		val m = Output(Bool())
//		val h = Output(Bool())
//		val s = Output(Bool())
//		val u = Output(Bool())
//	}
//
//	val csrNotImplemented = RegInit(UInt(XLEN.W), 0.U)
//
//	class MstatusStruct extends Bundle {
//		val sd = Output(UInt(1.W))
//
//		val pad1 = if (XLEN == 64) Output(UInt(27.W)) else null
//		val sxl  = if (XLEN == 64) Output(UInt(2.W))  else null
//		val uxl  = if (XLEN == 64) Output(UInt(2.W))  else null
//		val pad0 = if (XLEN == 64) Output(UInt(9.W))  else null
//
//		val tsr		= Output(UInt(1.W))
//		val tw		= Output(UInt(1.W))
//		val tvm		= Output(UInt(1.W))
//		val mxr		= Output(UInt(1.W))
//		val sum		= Output(UInt(1.W))
//		val mprv	= Output(UInt(1.W))
//		val xs		= Output(UInt(2.W))
//		val fs		= Output(UInt(2.W))
//		val mpp		= Output(UInt(2.W))
//		val hpp		= Output(UInt(2.W))
//		val spp		= Output(UInt(1.W))
//		val pie		= new Priv
//		val ie		= new Priv
//	}
//
//	class SatpStruct extends Bundle {
//		val mode	= UInt(4.W)
//		val asid	= UInt(16.W)
//		val ppn		= UInt(44.W)
//	}
//
//	class Interrupt extends Bundle { val e = new Priv
//		val t = new Priv
//		val s = new Priv
//	}
//
//	// Machine-Level CSRs
//	val mtvec	= RegInit(UInt(XLEN.W), 0.U)
//	val mcause	= RegInit(UInt(XLEN.W), 0.U)
////	val mtval	= RegInit(UInt(XLEN.W), 0.U)
//	val mepc	= RegInit(UInt(XLEN.W), 0.U)
//	val mstatus	= RegInit(UInt(XLEN.W), "ha00001800".U)
//	/* mstatus Value table */
//	/* 
//	| sd   |
//	| pad1 |
//	| sxl  | hardlinked to 10, use 00 to pass xv6 test
//	| uxl  | hardlinked to 10
//	| pad0 |
//	| tsr  |
//	| tw   |
//	| tvm  |
//	| mxr  |
//	| sum  |
//	| mprv |
//	| xs   | 00 |
//	| fs   | 00 |
//	| mpp  | 11 | Machine Previous Privilege
//	| hpp  | 00 |
//	| spp  | 0 |
//	| pie  | 0000 |
//	| ie   | 0000 |
//	*/
//	val mstatusStruct = mstatus.asTypeOf(new MstatusStruct)
//	def mstatusUpdateSideEffect(mstatus: UInt): UInt = {
//		val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
//		val mstatusNew = Cat(mstatusOld.fs === "b11".U, mstatus(XLEN-2, 0))
//		mstatusNew
//	}
//
//	// Superviser-Level CSRs
//	val satp = RegInit(UInt(XLEN.W), 0.U)
//
//	if (Settings.get("HasDTLB")) {
//		BoringUtils.addSource(satp, "CSRSATP")
//	}
//
//	// CSR Priviledge Mode
//	val priviledgeMode = RegInit(UInt(2.W), ModeM)
//
//
//	// CSR reg map
//	val mapping = Map (
//		// Supervisor Protection and Translation
//		MaskedRegMap(Satp, satp),
//
//		// Machine Trap Setup
//		MaskedRegMap(Mstatus, mstatus, "hffffffffffffffff".U(64.W), mstatusUpdateSideEffect),
//		MaskedRegMap(Mtvec, mtvec),
//
//		// Machine Trap Handing
//		MaskedRegMap(Mepc, mepc),
//		MaskedRegMap(Mcause, mcause)
//	)
//
//	val addr = srcB(11, 0)
//	val rdata = Wire(UInt(XLEN.W))
//	val csri = ZeroExt(io.cfIn.instr(19, 15), XLEN) // unsigned imm for csri. [TODO]
//	val wdata = LookupTree(ctrl, List(
//		CSRCtrl.wrt	-> srcA,
//		CSRCtrl.set	-> (rdata | srcA),
//		CSRCtrl.clr	-> (rdata & ~srcA),
//		CSRCtrl.wrti	-> csri, //TODO: csri --> srcB
//		CSRCtrl.seti	-> (rdata | csri),
//		CSRCtrl.clri	-> (rdata & ~csri)
//	))
//
//	// SATP wen check
//	val satpLegalMode = (wdata.asTypeOf(new SatpStruct).mode === 0.U) || (wdata.asTypeOf(new SatpStruct).mode === 8.U)
//
//	// General CSR wen check
//	val wen = (valid && ctrl =/= CSRCtrl.jmp) && (addr =/= Satp.U || satpLegalMode) && !io.isBackendException
//	val isIllegalMode = priviledgeMode < addr(9, 8)
//	val justRead = (ctrl === CSRCtrl.set || ctrl === CSRCtrl.seti) && srcA === 0.U // csrrs and csrrsi are exceptions when their srcA is zero
//	val isIllegalWrite = wen && (addr(11, 10) === "b11".U) && !justRead // Write a read-only CSR register
//	val isIllegalAccess = isIllegalMode || isIllegalWrite
//
//	MaskedRegMap.generate(mapping, addr, rdata, wen && !isIllegalAccess, wdata)
//	val isIllegalAddr = MaskedRegMap.isIllegalAddr(mapping, addr)
//	val resetSatp = addr === Satp.U && wen // write to satp will cause the pipeline be flushed
//	io.out.bits := rdata
//
//	// CSR inst decode
//	val ret = Wire(Bool())
//	val isEbreak = addr === privEbreak && ctrl === CSRCtrl.jmp && !io.isBackendException
//	val isEcall = addr === privEcall && ctrl === CSRCtrl.jmp && !io.isBackendException
//	val isMret = addr === privMret && ctrl === CSRCtrl.jmp && !io.isBackendException
//	val isSret = addr === privSret && ctrl === CSRCtrl.jmp && !io.isBackendException
//	val isUret = addr === privUret && ctrl === CSRCtrl.jmp && !io.isBackendException
//
//	// Exception and Intr
//	// interrupts
//	val intrNO = IntPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(io.cfIn.intrVec(i), i.U, sum))
//	val raiseIntr = io.cfIn.intrVec.asUInt.orR
//    // exceptions
//    val csrExpectionVec = Wire(Vec(16, Bool()))
//	csrExpectionVec.map(_ := false.B)
//	csrExpectionVec(breakPoint) := io.in.valid && isEbreak
//    csrExpectionVec(ecallM) := priviledgeMode === ModeM && io.in.valid && isEcall
//	csrExpectionVec(ecallS) := priviledgeMode === ModeS && io.in.valid && isEcall
//	csrExpectionVec(ecallU) := priviledgeMode === ModeU && io.in.valid && isEcall
//	csrExpectionVec(illegalInstr) := (isIllegalAddr || isIllegalAccess) && wen && !io.isBackendException // Trigger an illegal instr exception when unimplemented csr is being read/written or not having enough privilege
////	csrExpectionVec(loadPageFault) :=
//	val iduExceptionVec = io.cfIn.exceptionVec
//	val raiseExceptionVec = csrExpectionVec.asUInt | iduExceptionVec.asUInt
//	val raiseException = raiseExceptionVec.orR
//	val exceptionNO = ExcPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(raiseExceptionVec(i), i.U, sum))
//	io.wenFix := raiseException
//
//	val causeNO = (raiseIntr << (XLEN-1)) | Mux(raiseIntr, intrNO, exceptionNO)
//	io.intrNO := Mux(raiseIntr, causeNO, 0.U)
//
//	val raiseExceptionIntr = (raiseException || raiseIntr) && io.instrValid
//	val retTarget = Wire(UInt(VAddrBits.W))
//	val trapTarget = Wire(UInt(VAddrBits.W))
//	io.redirect.valid := (valid && ctrl === CSRCtrl.jmp) || raiseExceptionIntr || resetSatp
//	io.redirect.rtype := 0.U
//	io.redirect.target := Mux(resetSatp, io.cfIn.pc + 4.U, Mux(raiseExceptionIntr, trapTarget, retTarget))
//
//	// Branch control
//	ret := isMret || isSret || isUret
//	trapTarget := mtvec(VAddrBits-1, 0)
//	retTarget := DontCare
//
//	when (valid && isMret) {
//		val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
//		val mstatusNew = WireInit(mstatus.asTypeOf(new MstatusStruct))
//		mstatusNew.ie.m := mstatusOld.pie.m
//		priviledgeMode := mstatusOld.mpp
//		mstatusNew.pie.m := true.B
//		mstatusNew.mpp := ModeU
//		mstatus := mstatusNew.asUInt
////		lr := false.B
//		retTarget := mepc(VAddrBits-1, 0)
//	}
//
//	when (raiseExceptionIntr) {
//		val mstatusOld = WireInit(mstatus.asTypeOf(new MstatusStruct))
//		val mstatusNew = WireInit(mstatus.asTypeOf(new MstatusStruct))
//
//		// TODO support delegS
//		mcause := causeNO
//		mepc := SignExt(io.cfIn.pc, XLEN)
//		mstatusNew.mpp := priviledgeMode
//		mstatusNew.pie.m := mstatusOld.ie.m
//		mstatusNew.ie.m := false.B
//		priviledgeMode := ModeM
////		when(tvalWen) {mtval := 0.U}
//
//		mstatus := mstatusNew.asUInt
//	}
//	io.in.ready := true.B
//	io.out.valid := valid
//
//	if (!Settings.get("IsChiselTest")) {
//		io.csr.regs(0) := mstatus
//		io.csr.regs(1) := mtvec
//		io.csr.regs(2) := mepc
//		io.csr.regs(3) := mcause
//	}
//}
//