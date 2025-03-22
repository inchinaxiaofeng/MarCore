package bus.axi4

import chisel3._
import chisel3.util._

import defs._
import bus.axi4._
import utils._

class AXI4Lite_Arbiter extends MarCoreModule {
	implicit val moduleName:String = this.name
	val InstFetch	= IO(Flipped(new AXI4Lite))
	val LoadStore	= IO(Flipped(new AXI4Lite))
	val Arbiter		= IO(new AXI4Lite)

	// Rule: LoadStore > InstFetch
	// This is the interface between the
	// "module part external" and
	// "SoC/Simulation external"

	InstFetch	:= DontCare
	LoadStore	:= DontCare
	Arbiter		:= DontCare

	LoadStore.aw	<> Arbiter.aw
	LoadStore.w		<> Arbiter.w
	LoadStore.b		<> Arbiter.b

	val s_idle :: s_if_exec :: s_ls_exec :: Nil = Enum(3)
	val state = RegInit(s_idle)

	switch (state) {
		is (s_idle) {
			when (InstFetch.ar.valid && LoadStore.ar.valid) {
				Info("[LoadStore <===> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				LoadStore.ar <> Arbiter.ar
				LoadStore.r  <> Arbiter.r
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				state := s_ls_exec
			}.elsewhen (InstFetch.ar.valid && !LoadStore.ar.valid) {
				Info("[InstFetch <===> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				InstFetch.ar <> Arbiter.ar
				InstFetch.r  <> Arbiter.r
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
				state := s_if_exec
			}.elsewhen (!InstFetch.ar.valid && LoadStore.ar.valid) {
				Info("[LoadStore <===> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				LoadStore.ar <> Arbiter.ar
				LoadStore.r  <> Arbiter.r
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				state := s_ls_exec
			}.otherwise {
				Info("[DONT CARE <=X=> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
			}
		}

		is (s_if_exec) {
			Info("[InstFetch <===> SRAM] exec ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
			InstFetch.ar <> Arbiter.ar
			InstFetch.r  <> Arbiter.r
			LoadStore.ar.ready := false.B
			LoadStore.r.valid := false.B
			LoadStore.r.bits.apply()
			when (InstFetch.r.fire) { state := s_idle }
		}

		is (s_ls_exec) {
			Info("[LoadStore <===> SRAM] exec ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
			LoadStore.ar <> Arbiter.ar
			LoadStore.r  <> Arbiter.r
			InstFetch.ar.ready := false.B
			InstFetch.r.valid := false.B
			InstFetch.r.bits.apply()
			when (LoadStore.r.fire) { state := s_idle }
		}
	}
}

class AXI4_Arbiter extends MarCoreModule {
	implicit val moduleName:String = this.name
	val InstFetch	= IO(Flipped(new AXI4))
	val LoadStore	= IO(Flipped(new AXI4))
	val Arbiter		= IO(new AXI4)

	// Rule: LoadStore > InstFetch
	// This is the interface between the
	// "module part external" and
	// "SoC/Simulation external"

	InstFetch := DontCare
	LoadStore := DontCare
	Arbiter := DontCare

	LoadStore.aw	<> Arbiter.aw
	LoadStore.w		<> Arbiter.w
	LoadStore.b		<> Arbiter.b

	val s_idle :: s_if_exec :: s_ls_exec :: Nil = Enum(3)
	val state = RegInit(s_idle)

	switch (state) {
		is (s_idle) {
			when (InstFetch.ar.valid && LoadStore.ar.valid) {
				Info("[LoadStore <=-=> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				LoadStore.ar <> Arbiter.ar
				LoadStore.r  <> Arbiter.r
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				state := s_ls_exec
			}.elsewhen (InstFetch.ar.valid && !LoadStore.ar.valid) {
				Info("[InstFetch <=-=> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				InstFetch.ar <> Arbiter.ar
				InstFetch.r  <> Arbiter.r
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
				state := s_if_exec
			}.elsewhen (!InstFetch.ar.valid && LoadStore.ar.valid) {
				Info("[LoadStore <=-=> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				LoadStore.ar <> Arbiter.ar
				LoadStore.r  <> Arbiter.r
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				state := s_ls_exec
			}.otherwise {
				Info("[DONT CARE <=X=> SRAM] idle ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
			}
		}

		is (s_if_exec) {
			Info("[InstFetch <===> SRAM] exec ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
			InstFetch.ar <> Arbiter.ar
			InstFetch.r  <> Arbiter.r
			LoadStore.ar.ready := false.B
			LoadStore.r.valid := false.B
			LoadStore.r.bits.apply()
			when (InstFetch.r.fire && InstFetch.r.bits.last) { state := s_idle }
		}

		is (s_ls_exec) {
			Info("[LoadStore <===> SRAM] exec ifvr%x,%x lsvr%x,%x\n", InstFetch.ar.valid, InstFetch.ar.ready, LoadStore.ar.valid, LoadStore.ar.ready)
			LoadStore.ar <> Arbiter.ar
			LoadStore.r  <> Arbiter.r
			InstFetch.ar.ready := false.B
			InstFetch.r.valid := false.B
			InstFetch.r.bits.apply()
			when (LoadStore.r.fire && LoadStore.r.bits.last) { state := s_idle }
		}
	}
}

class AXI4_Arbiter_MMIO extends MarCoreModule {
	implicit val moduleName:String = this.name
	val InstFetch	= IO(Flipped(new AXI4))
	val LoadStore	= IO(Flipped(new AXI4))
	val MMIO		= IO(Flipped(new AXI4))
	val Arbiter		= IO(new AXI4)

	InstFetch	:= DontCare
	LoadStore	:= DontCare
	MMIO		:= DontCare
	Arbiter		:= DontCare

	val s_idle :: s_if_exec :: s_ls_exec :: s_mmio_exec :: Nil = Enum(4)

	val read_state = RegInit(s_idle)
	switch (read_state) {
		is (s_idle) {
			when (MMIO.ar.valid) {
				MMIO.ar <> Arbiter.ar
				MMIO.r <> Arbiter.r
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				read_state := s_mmio_exec
			}.elsewhen (LoadStore.ar.valid) {
				LoadStore.ar <> Arbiter.ar
				LoadStore.r <> Arbiter.r
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				MMIO.ar.ready := false.B
				MMIO.r.valid := false.B
				MMIO.r.bits.apply()
				read_state := s_ls_exec
			}.elsewhen (InstFetch.ar.valid) {
				InstFetch.ar <> Arbiter.ar
				InstFetch.r <> Arbiter.r
				MMIO.ar.ready := false.B
				MMIO.r.valid := false.B
				MMIO.r.bits.apply()
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
				read_state := s_if_exec
			}.otherwise {
				InstFetch.ar.ready := false.B
				InstFetch.r.valid := false.B
				InstFetch.r.bits.apply()
				LoadStore.ar.ready := false.B
				LoadStore.r.valid := false.B
				LoadStore.r.bits.apply()
				MMIO.ar.ready := false.B
				MMIO.r.valid := false.B
				MMIO.r.bits.apply()
			}
		}

		is (s_mmio_exec) {
			MMIO.ar <> Arbiter.ar
			MMIO.r <> Arbiter.r
			LoadStore.ar.ready := false.B
			LoadStore.r.valid := false.B
			LoadStore.r.bits.apply()
			InstFetch.ar.ready := false.B
			InstFetch.r.valid := false.B
			InstFetch.r.bits.apply()
			when (MMIO.r.fire && MMIO.r.bits.last) { read_state := s_idle }
		}

		is (s_ls_exec) {
			LoadStore.ar <> Arbiter.ar
			LoadStore.r <> Arbiter.r
			MMIO.ar.ready := false.B
			MMIO.r.valid := false.B
			MMIO.r.bits.apply()
			InstFetch.ar.ready := false.B
			InstFetch.r.valid := false.B
			InstFetch.r.bits.apply()
			when (LoadStore.r.fire && LoadStore.r.bits.last) { read_state := s_idle }
		}

		is (s_if_exec) {
			InstFetch.ar <> Arbiter.ar
			InstFetch.r <> Arbiter.r
			MMIO.ar.ready := false.B
			MMIO.r.valid := false.B
			MMIO.r.bits.apply()
			LoadStore.ar.ready := false.B
			LoadStore.r.valid := false.B
			LoadStore.r.bits.apply()
			when (InstFetch.r.fire && InstFetch.r.bits.last) { read_state := s_idle }
		}
	}

	val write_state = RegInit(s_idle) // Ban s_if_exec
	switch (write_state) {
		is (s_idle) {
			when (MMIO.aw.valid) {
				MMIO.aw <> Arbiter.aw
				MMIO.w <> Arbiter.w
				MMIO.b <> Arbiter.b
				LoadStore.aw.ready := false.B
				LoadStore.w.ready := false.B
				LoadStore.b.valid := false.B
				LoadStore.b.bits.apply()
				write_state := s_mmio_exec
			}.elsewhen (LoadStore.aw.valid) {
				LoadStore.aw <> Arbiter.aw
				LoadStore.w <> Arbiter.w
				LoadStore.b <> Arbiter.b
				MMIO.aw.ready := false.B
				MMIO.w.ready := false.B
				MMIO.b.valid := false.B
				MMIO.b.bits.apply()
				write_state := s_ls_exec
			}.otherwise {
				MMIO.aw.ready := false.B
				MMIO.w.ready := false.B
				MMIO.b.valid := false.B
				MMIO.b.bits.apply()
				LoadStore.aw.ready := false.B
				LoadStore.w.ready := false.B
				LoadStore.b.valid := false.B
				LoadStore.b.bits.apply()
			}
		}

		is (s_mmio_exec) {
			MMIO.aw <> Arbiter.aw
			MMIO.w <> Arbiter.w
			MMIO.b <> Arbiter.b
			LoadStore.aw.ready := false.B
			LoadStore.w.ready := false.B
			LoadStore.b.valid := false.B
			LoadStore.b.bits.apply()
			when (MMIO.b.fire) { write_state := s_idle }
		}

		is (s_ls_exec) {
			LoadStore.aw <> Arbiter.aw
			LoadStore.w <> Arbiter.w
			LoadStore.b <> Arbiter.b
			MMIO.aw.ready := false.B
			MMIO.w.ready := false.B
			MMIO.b.valid := false.B
			MMIO.b.bits.apply()
			when (LoadStore.b.fire) { write_state := s_idle }
		}
	}
}
