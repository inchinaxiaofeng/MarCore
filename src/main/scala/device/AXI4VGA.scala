//package device
//
//import chisel3._
//import chisel3.util._
//
//import bus.axi4._
//import utils._
//import top._
//
//trait HasVGAConst {
//	val _vgaM: VGAMode = Settings.getT("VGAMode")
//
//	// current not support 640x480
//	val ScreenW = _vgaM.hvalid
//	val ScreenH = _vgaM.vvalid
//
////	val HFrontPorch = _vgaM.hfront
////	val HActive = _vgaM.hfront + _vgaM.hsync
////	val HBackPorch = _vgaM.hfront + _vgaM.hsync + _vgaM.hvalid
////	val HTotal = _vgaM.hfront + _vgaM.hsync + _vgaM.hvalid + _vgaM.hback
////	val VFrontPorch = _vgaM.vfront
////	val VActive = _vgaM.vfront + _vgaM.vsync
////	val VBackPorch = _vgaM.vfront + _vgaM.vsync + _vgaM.vvalid
////	val VTotal = _vgaM.vfront + _vgaM.vsync + _vgaM.vvalid + _vgaM.vback
//}
//
//trait HasVGAParameter extends HasVGAConst {
//	val FBWidth = ScreenW / 2
//	val FBWidth = ScreenH / 2
//	val FBPixels = FBWidth * FBHeight
//}
//
//class VGABundle extends Bundle {
//	val _rgbM: RGBMode = Settings.getT("RGBMode")
//	val rgbLen = _rgbM.r+_rgbM.g+_rgbM.b
//	val rgb = Output(UInt(rgbLen.W))
//	val hsync = Output(Bool())
//	val vsync = Output(Bool())
//}
//
//class VGACtrlBundle extends Bundle {
//	val sync = Output(Bool())
//}
//
//class VGACtrl extends AXI4SlaveModule (new AXI4Lite, new VGACtrlBundle) with HasVGAParameter {
//	val fbSizeReg = Cat(FBWidth.U(16.W), FBHeight.U(16.W))
//	val sync = in.aw.fire
//
//	val mapping = Map(
//		RegMap(0x0, fbSizeReg, RegMap.Unwritable)
//		RegMap(0x4, sync, RegMap.Unwritbale)
//	)
//
//	RegMap.generate(mapping, raddr(3,0), in.r.bits.data,
//		waddr(3,0), in.w.fire, in.w.bits.data, MaskExpand(in.w.bits.strb)
//
//	io.extra.get.sync := sync
//}
//
//class VGA_PIC extends Module with {
//	val io = IO(new Bundle {
//		val vga
//	})
//}
//
//class AXI4VGA(sim: Boolean = false) extends Module with HasVGAParameter {
//	val AXIidBits = 2
//	val io = IO(new Bundle {
//		val in = new Bundle {
//			val fb = Flipped(new AXI4Lite)
//			val ctrl = Flipped(new AXI4Lite)
//		}
//		val vga = new VGABundle
//	})
//	val ctrl = Module(new VGACtrl)
//	io.in.ctrl <> ctrl.io.in
//	val fb = Module(new AXI4RAM(new AXI4Lite, memByte = FBPixels * 4))
//	// Writeable by axi4lite
//	// but it only readable by the internel controller
//	fb.io.in.aw <> io.in.fb.aw
//	fb.io.in.w <> io.in.fb.w
//	io.in.fb.b <> fb.io.in.b
//	io.in.fb.ar.ready := true.B
//	io.in.fb.r.bits.data := 0.U
//	io.in.fb.r.bits.resp := AXI4Parameters.RESP_OKAY
//	io.in.fb.r.valid := BoolStopWatch(io.in.fb.ar.fire, io.in.fb.r.fire, startHighPriority = true)
//
////	val vga_fb_used = RegInit(false.B)
////	val fb_req = io.in.fb
////	when (fb_req.aw.valid || fb_req.w.valid || fb_req.ar.valid) {
////		vga_fb_used := true.B
////	}
//
//	def inRange(x: UInt, start: Int, end: Int) = (x >= start.U) && (x < end.U)
//
//	val (hCounter, hFinish) = Counter(true.B, _vgaM.htotal)
//	val (vCounter, vFinish) = Counter(hFinish, _vgaM.vtotal)
//
//	// 图像有效信号
//	val hInRange = inRange(hCounter, _vgaM.hsync+_vgaM.hback+_vgaM.hleft, _vgaM.hsync+_vgaM.hback+_vgaM.hleft+_vgaM.hvalid)
//	val vInRange = inRange(vCounter, _vgaM.vsync+_vgaM.vback+_vgaM.vtop, _vgaM.vsync+_vgaM.vback+_vgaM.vtop+_vgaM.vvalid)
//	val rgbValid = hInRange && vInRange
//
//	// 数据请求信号
//	// 行计数器往前提前一个，代表超前一个时钟周期
//	// 场计数器往前提前一个，代表超前一行，不可取
//	val hInRangeBeyond = inRange(hCounter, _vgaM.hsync+_vgaM.hback+_vgaM.hleft-1.U, _vgaM.hsync+_vgaM.hback+_vgaM.hleft+_vgaM.hvalid-1.U)
//	val pix_data_req = hInRangeBeyond && vInRange
//
//	io.vga.hsync := hCounter < _vgaM.hsync.U
//	io.vga.vsync := vCounter < _vgaM.vsync.U
//	io.vga.rgb := Mux(rgbValid === true.B, pix_data, 0.U)
////------------------------------------------------------------------------
//	val ctrl = Module(new VGACtrl)
//	io.in.ctrl <> ctrl.io.in
//	val fb = Module(new AXI4RAM(new AXI4Lite, memByte = FBPixels * 4))
//	// Writeable by axi4lite
//	// but it only readable by the internel controller
//	fb.io.in.aw <> io.in.fb.aw
//	fb.io.in.w <> io.in.fb.w
//	io.in.fb.b <> fb.io.in.b
//	io.in.fb.ar.ready := true.B
//	io.in.fb.r.bits.data := 0.U
//	io.in.fb.r.bits.resp := AXI4Parameters.RESP_OKAY
//	io.in.fb.r.valid := BoolStopWatch(io.in.fb.ar.fire, io.in.fb.r.fire, startHighPriority = true)
//
//	val vga_fb_used = RegInit(false.B)
//	val fb_req = io.in.fb
//	when (fb_req.aw.valid || fb_req.w.valid || fb_req.ar.valid) {
//		vga_fb_used := true.B
//	}
//
//	def inRange(x: UInt, start: Int, end: Int) = (x >= start.U) && (x < end.U)
//
//
//	io.vga.hsync := hCounter >= HFrontPorch.U
//	io.vga.vsync := vCounter >= VFrontPorch.U
//
//	val hInRange = inRange(hCounter, HActive, HBackPorch)
//	val vInRange = inRange(vCounter, VActive, VBackPorch)
//
//	val hCounterIsOdd = hCounter(0)
//	val hCounterIs2 = hCounter(1, 0) === 2.U
//	val vCounterIsOdd = vCounter(0)
//	// there is 2 cycle latency to read block memory,
//	// so we should issue the read request 2 cycle eariler
//	val nextPixel = inRange(hCounter, HActive-1, HBackPorch-1) && vInRange && hCounterIsOdd
//	val fbPixelAddrV0 = Counter(nextPixel && !vCounterIsOdd, FBPixels)._1
//	val fbPixelAddrV1 = Counter(nextPixel &&  vCounterIsOdd, FBPixels)._1
//
//	// each pixel is 4 bytes
//	fb.io.in.ar.bits.prot := 0.U
//	fb.io.in.ar.bits.addr := Cat(Mux(vCounterIsOdd, fbPixelAddrV1, fbPixelAddrV0), 0.U(2.W))
//	fb.io.in.ar.valid := RegNext(nextPixel) && hCounterIs2
//
//	fb.io.in.r.ready := true.B
//	val data = HoldUnless(fb.io.in.r.bits.data, fb.io.in.r.fire)
//	val coler = if (Settings.get("IsRV32")) data(31, 0)
//				else Mux(hCounter(1), data(63, 32), data(31, 0))
//	io.vga.rgb := color(rgbLen-1, 0)//Mux(io.vga.valid, color(rgbLen, 0), 0.U)
//}
