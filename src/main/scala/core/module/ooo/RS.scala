//package module.ooo
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import utils._
//
//trait HasRSConst {
//	val rsCommitWidth = 2
//}
//
//// Reservation Station for Out Of Order Execution Backend
//class RS(size: Int = 2, pipelined: Boolean = true, fifo: Boolean = false,
//priority: Boolean = false, checkpoint: Boolean = false,
//storeBarrier: Boolean = false, storeSeq: Boolean = false,
//name: String = "unnamedRS")
//extends MarCoreModule with HasRSConst with HasBackendConst {
//	val io = IO(new Bundle {
//		val in = Flipped(Decoupled(new RenamedDecodeIO))
//		val out = Decoupled(new RenamedDecodeIO)
//		val cdb = Vec(rsCommitWidth, Flipped(Valid(new OOCommitIO)))
//		val mispredictRec = Flipped(new MisPredictRecIO)
//		val flush = Input(Bool())
//		val empty = Output(Bool())
//		val updateCheckPoint = if (checkpoint) Some(Output(Valid(UInt(log2Up(size).W)))) else None
//		val recoverCheckPoint = if (checkpoint) Some(Output(Valid(UInt(log2Up(size).W)))) else None
//		val freeCheckPoint = if (checkPoint) Some(Input(Valid(UInt(log2Up(size).W)))) else None
//		val stMaskOut = if (storeSeq) Some(Output(UInt(robSize.W))) else None
//		val commit = if (!pipelined) Some(Input(Bool())) else None
//	})
//
//	val rsSize	= size
//	val decode	= Mem(rsSize, new RenamedDecodeIO)
//	val valid	= RegInit(VecInit(Seq.fill(rsSize)(false.B)))
//	val srcARdy	= RegInit(VecInit(Seq.fill(rsSize)(false.B)))
//	val srcBRdy	= RegInit(VecInit(Seq.fill(rsSize)(false.B)))
//	val brMask	= RegInit(VecInit(Seq.fill(rsSize)(0.U(checkpointSize.W))))
//	val prfSrcA	= Reg(Vec(rsSize, UInt(prfAddrWidth.W)))
//	val prfSrcB	= Reg(Vec(rsSize, UInt(prfAddrWidth.W)))
//	val srcA	= Reg(Vec(rsSize, UInt(XLEN.W)))
//	val srcB	= Reg(Vec(rsSize, UInt(XLEN.W)))
//
//	val validNext = WireInit(valid)
//	val instRdy = WireInit(VecInit(List.tabulate(rsSize)(i => srcARdy(i) && srcBRdy(i) && valid(i))))
//	val rsEmpty = !valid.asUInt.orR
//	val rsFull = valid.asUInt.andR
//	val rsAllowin = !rsFull
//	val rsReadygo = Wire(Bool())
//	rsReadygo := instRdy.foldRight(false.B)((sum, i) => sum|i)
//
//	val priorityMask = RegInit(VecInit(Seq.fill(rsSize)(VecInit(Seq.fill(rsSize)(false.B)))))
//	val forceDequeue = WireInit(false.B)
//
//	def needMispredictionRecovery(brMask: UInt) = {
//		io.mispredictRec.valid && io.mispredictRec.redirect.valid && brMask(io.mispredictRec.checkpoint)
//	}
//
//	def updateBrMask(brMask: UInt) = {
//		brMask & ~(UIntToOH(io.mispredictRec.checkpoint) & Fill(checkpointSize, io.mispredictRec.valid))
//	}
//
//	// Listen to Common Data Bus
//	// Here we listen to commit signal chosen by ROB?
//	// If prf === src, mark it as 'ready'
//
//	List.tabulate(rsSize)(i =>
//		when (valid(i)) {
//			List.tabulate(rsCommitWidth)(j =>
//				when (!srcARdy(i) && prfSrcA(i) === io.cdb(j).bits.prfidx && io.cdb(j).valid){
//					srcARdy(i) := true.B
//					srcA(i) := io.cdb(j).bits.commits
//				}
//			)
//			List.tabulate(rsCommitWidth)(j =>
//				when (!srcARdy(i) && prfSrcB(i) === io.cdb(j).bits.prfidx && io.cdb(j).valid) {
//					srcBRdy(i) := true.B
//					srcB(i) := io.cdb(j).bits.commits
//				}
//			)
//			// Update RS according to brMask
//			// If a branch inst is canceled by BRU, the following insts with subject to this branch should also be canceled
//			brMask(i) := updateBrMask(brMask(i))
//			when(needMispredictionRecovery(brMask(i))) { valid(i) := false.B }
//		}
//	)
//
//	// RS enqueue
//	io.in.ready := rsAllowin
//	io.empty := rsEmpty
//	val emptySlot = ~valid.asUInt
//	val enqueueSelect = PriorityEncoder(emptySlot) // TODO: replace PriorityEncoder with other logic
//
//	when (io.in.fire) {
//		decode(enqueueSelect) := io.in.bits
//		valid(enqueueSelect) := true.B
//		prfSrcA(enqueueSelect) := io.in.bits.prfSrcA
//		prfSrcB(enqueueSelect) := io.in.bits.prfSrcB
//		srcARdy(enqueueSelect) := io.in.bits.srcARdy
//		srcBRdy(enqueueSelect) := io.in.bits.srcBRdy
//		srcA(enqueueSelect) := io.in.bits.decode.data.srcA
//		srcB(enqueueSelect) := io.in.bits.decode.data.srcB
//		brMask(enqueueSelect) := io.in.bits.brMask
//	}
//
//	// RS dequeue
//	val dequeueSelect = Wire(UInt(log2Up(size).W))
//	dequeueSelect := PriorityEncoder(instRdy)
//	when (io.out.fire || forceDequeue) {
//		if (!checkpoint) {
//			valid(dequeueSelect) := false.B
//		}
//	}
//
//	io.out.valid := rsReadygo // && validNext(dequeueSelect)
//	io.out.bits := decode(dequeueSelect)
//	io.out.bits.brMask := brMask(dequeueSelect)
//	io.out.bits.decode.data.srcA := srcA(dequeueSelect)
//	io.out.bits.decode.data.srcB := srcB(dequeueSelect)
//
//	when (io.flush) {
//		List.tabulate(rsSize)(i =>
//			valid(i) := false.B
//		)
//	}
//
//	Debug(io.out.fire, "[ISSUE-"+name+"] " + "TIMER: %d pc = 0x%x inst %x wen %x id %d\n", GTimer(), io.out.bits.decode.cf.pc, io.out.bits.decode.cf.instr, io.out.bits.decode.ctrl.rfWen, io.out.bits.prfDest)
//
//	Debug("[RS "+name+"] pc           v src1               src2\n")
//	for (i <- o to (size - 1)) {
//		Debug("[RS "+name+"] 0x%x %x %x %x %x %x %d", decode(i).decode.cf.pc, valid(i), srcARdy(i), srcA(i), srcBRdy(i), srcB(i), decode(i).prfDest)
//		Debug(valid(i), "  valid")
//		Debug(" mask %x\n", brMask(i))
//	}
//
//	// Fix unpipelined
//	// when `pipelined` === false, RS helps unpipelined FU to store its uop for commit
//	// if an unpipelined fu can store uop itself, set `pipelined` to true (it behaves just like a pipelined FU)
//	if (!pipelined) {
//		val fuValidReg = RegInit(false.B)
//		val brMaskPReg = RegInit(0.U(checkpointSize.W))
//		val fuFlushReg = RegInit(false.B)
//		val fuDecodeReg = RegEnable(io.out.bits, io.out.fire)
//		brMaskPReg := updateBrMask(brMaskPReg)
//		when (io.out.fire) {
//			fuValidReg := true.B
//			brMaskPReg := updateBrMask(brMask(dequeueSelect))
//		}
//		when (io.commit.get) { fuValidReg := false.B }
//		when (io.flush ||
//			needMispredictionRecovery(brMaskPReg)) &&
//			fuValidReg ||
//			(io.flush ||
//				needMispredictionRecovery(brMask(dequeueSelect))) &&
//			io.out.fire) { fuFlushReg := true.B }
//		when (io.commit.get) { fuFlushReg := false.B }
//		when (fuValidReg) { io.out.bits := fuDecodeReg }
//		when (fuValidReg) { io.out.valid := true.B && !fuFlushReg }
//		io.out.bits.brMask := brMaskPReg
//		Debug("[RS "+name+"] pc 0x%x valid %x flush %x brMaskPReg %x prfidx %d  in %x\n", fuDecodeReg.decode.cf.pc, fuValidReg, fuFlushReg, brMaskPReg, fuDecodeReg.prDest, io.out.fire)
//	}
//
//	if (fifo) {
//		require(!priority)
//		require(!storeBarrier)
//		dequeueSelect := OHToUInt(List.tabulate(rsSize)(i => {
//			!priorityMask(i).asUInt.orR && valid(i)
//		}))
//		// update priorityMask
//		io.out.valid := instRdy(dequeueSelect)
//		List.tabulate(rsSize)(i =>
//			when (needMispredictionRecovery(brMask(i))) {
//				List.tabulate(rsSize)(j =>
//					priorityMask(j)(i) := false.B
//				)
//			}
//		)
//		when (io.in.fire) { priorityMask(enqueueSelect) := valid }
//		when (io.out.fire) { (0 until rsSize).map(i => priorityMask(i)(dequeueSelect) := false.B) }
//		when (io.flush) { (0 until rsSize).map(i => priorityMask(i) := VecInit(Seq.fill(rsSize)(false.B))) }
//	}
//
//	if (priority) {
//		require(!fifo)
//		require(!storeBarrier)
//		dequeueSelect := OHToUInt(List.tabulate(rsSize)(i => {
//			!(priorityMask(i).asUInt & instRdy.asUInt).orR & instRdy(i)
//		}))
//		// update priorityMask
//		List.tabulate(rsSize)(i =>
//			when (needMispredictionRecovery(brMask(i))) {
//				List.tabulate(rsSize)(j =>
//					priorityMask(j)(i) := false.B
//				)
//			}
//		)
//		when (io.in.fire) { priorityMask(enqueueSelect) := valid }
//		when (io.out.fire) { (0 until rsSize).map(i => priorityMask(i)(dequeueSelect) := false.B) }
//		when (io.flush) { (0 until rsSize).map(i => priorityMask(i) := VecInit(Seq.fill(rsSize)(false.B))) }
//	}
//
//	if (storeBarrier) {
//		require(!println)
//		require(!fifo)
//		val needStore = Reg(Vec(rsSize, Bool()))
//		val dequeueSelectVec = VecInit(List.tabulate(rsSize)(i => {
//			valid(i) &&
//			Mux(
//				needStore(i),
//				!(priorityMask(i).asUInt.orR), // there is no other inst ahead
//				!((priorityMask(i).asUInt & needStore.asUInt).orR) // there is no store (with no valid addr) ahead
//			) &&
//			!(priorityMask(i).asUInt &  instRdy.asUInt).orR & instRdy(i)
//		}))
//		dequeueSelect := OHToUInt(dequeueSelectVec)
//		io.out.valid := instRdy(dequeueSelect) && dequeueSelectVec(dequeueSelect)
//		// update priorityMask
//		List.tabulate(rsSize)(i =>
//			when (needMispredictionRecovery(brMask(i))) {
//				List.tabulate(rsSize)(j =>
//					priorityMask(j)(i) := false.B
//				)
//			}
//		)
//		when (io.in.fire) {
//			priorityMask(enqueueSelect) := valid
//			needStore(enqueueSelect) := LSUOpType.needMemWrite(io.in.bits.decode.ctrl.fuOpType)
//		}
//		when (io.out.fire) { (0 until rsSize).map(i => priorityMask(i)(dequeueSelect) := false.B) }
//		when (io.flush) { (0 unitl rsSize).map(i => priorityMask(i) := VecInit(Seq.fill(rsSize)(false.B))) }
//	}
//
//	if (storeSeq) {
//		require(!priority || !fifo || !storeBarrier)
//		val stMask = Reg(Vec(rsSize, Vec(robSize, Bool())))
//		val needStore = Reg(Vec(rsSize, Bool()))
//		val stMaskReg = RegInit(VecInit(Seq.fill(robSize)(false.B)))
//		if (EnableOutOfOrderMemAccess) {
//			val dequeueSelectVec = VecInit(List.tabulate(rsSize)(i => {
//				valid(i),
//				Mux(
//					needStore(i),
//					!(priorityMask(I).asUInt.orR), // there is no other inst ahead
//					true.B
//				) &&
//				!(priorityMask(i).asUInt & instRdy.asUInt).orR & instRdy(i)
//			}))
//			dequeueSelect := OHToUInt(dequeueSelectVec)
//			io.out.valid := instRdy(dequeueSelect) && dequeueSelectVec(dequeueSelect)
//		} else {
//			dequeueSelect := OHToUInt(List.tabulate(rsSize)(i => {
//				!priorityMask(i).asUInt.orR && valid(i)
//			}))
//			io.out.valid := instRdy(dequeueSelect) && !priorityMask(dequeueSelect).asUInt.orR && valid(dequeueSelect)
//		}
//		// update priorityMask
//		List.tabulate(rsSize)(i =>
//			when (needMispredictionRecovery(brMask(i))) {
//				List.tabulate(rsSize)(j =>
//					priorityMask(j)(i) := false.B
//				)
//			}
//		)
//		when (io.in.fire) {
//			priorityMask(enqueueSelect) := valid
//			needStore(enqueueSelect) := LSUOpType.needMemWrite(io.in.bits.decode.ctrl.fuCtrl)
//		}
//		when (io.out.fire) { (0 until rsSize).map(i => priorityMask(i)(dequeueSelect) := false.B) }
//		when (io.flush) { (0 until rsSize).map(i => priorityMask(i) := VecInit(Seq.fill(rsSize)(false.B))) }
//
//		// update stMask
//		val robStoreInstVec = WireInit(0.U(robSize.W))
//		BoringUtils.adddSink(rob) ???
//		(0 until robSize).map(i => {
//			when (!robStoreInstVec(i)) { stMaskReg(i) := false.B }
//		})
//		when (io.in.fire && LSUOpType.needMemWrite(io.in.bits.decode.ctrl.fuCtrl)) {
//			stMaskReg(io.in.bits.prfDest(prfAddrWidth-1,1)) := true.B
//		}
//		when (io.in.fire) {
//			stMask(enqueueSelect) := stMaskReg
//		}
//		when (io.out.fire) {
//			stMaskReg(io.out.bits.prfDest(prfAddrWidth-1,1)) := true.B
//			(0 until rsSize).map(i =>
//				stMask(i)(io.out.bits.prfDest(prfAddrWidth-1,,1)) := false.B
//			)
//		}
//		when (io.flush) { (0 until robSize).map(i => {
//			stMaskReg(i) := false.B
//		})}
//		io.stMaskOut.get := stMaskReg(dequeueSelect).asUInt
//	}
//
//	if (checkpoint) {
//		require(priority)
//
//		io.updateCheckPoint.get.valid := io.in.fire
//		io.updateCheckPoint.get.bits := enqueueSelect
//		io.recoverCheckPoint.get.valid := DontCare
//		io.recoverCheckPoint.get.bits := dequeueSelect
//
//		val pending = RegInit(VecInit(Seq.fill(rsSize)(false.B)))
//		when (io.in.fire) { pending(enqueueSelect) := true.B }
//		when (io.out.fire) { pending(dequeueSelect) := false.B }
//		when (io.flush) { List.tabulate(rsSize)(i => pending(i) := false.B) }
//		instRdy := VecInit(List.tabulate(rsSize)(i => srcARdy(i) && srcBRdy(i) && valid(i) && pending(i)))
//		when (io.freeCheckPoint.get.valid) { valid(io.freeCheckPoint.get.bits) := false.B }
//
//		List.tabulate(rsSize)(i =>
//			when (needMispredictionRecovery(brMask(i))) {
//				List.tabulate(rsSize)(j =>
//					priorityMask(j)(i) := false.B
//				)
//			}
//		)
//		when (io.in.fire) { priorityMask(enqueueSelect) := (valid.asUInt & pending.asUInt).asBools }
//		when (io.out.fire) { (0 until rsSize).map(i => priorityMask(i)(dequeueSelect) := false.B) }
//		when (io.flush) { (0 until rsSize).map(i => priorityMask(i) := VecInit(Seq.fill(rsSize)(false.B))) }
//	}
//}
