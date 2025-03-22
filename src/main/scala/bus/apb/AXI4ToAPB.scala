//package bus.apb
//
//import chisel3._
//import chisel3.util._
//
//import defs._
//import utils._

//case class AXI4ToAPBNode()(implicit valName: ValName) extends MixedAdapterNode(AXI4Imp, APBImp)(
//    dFn = { mp =>
//        APBMasterPortParameters(
//            masters = mp.masters.map { m => APBMasterPortParameters(name = m.name, nodePath = m.nodePath) },
//            requestFields = mp.requestFields.filter(!_.isInstanceOf[AMBAProtField]),
//            responseKeys = mp.responseKeys
//        )
//    },
//    uFn = { sp =>
//        val beatBytes = 4
//        AXI4SlavePortParameters(
//            slaves = sp.slaves.map { s =>
//                val maxXfer = TransferSizes(1, beatBytes)
//                require(beatBytes == 4) // only support 8-bytes data AXI
//                AXI4SlavePortParameters(
//                    address = s.address,
//                    resources = s.resources,
//                    regionType = s.regionType,
//                    executable = s.executable,
//                    nodePath = s.nodePath
//                  )
//            }
//        )
//    }
//)
