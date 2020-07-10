package io.shiftleft.fuzzyc2cpg.output.inmemory

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.ProtoToCpg
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule
import io.shiftleft.passes.{DiffGraph, KeyPool}
import java.io.IOException

import io.shiftleft.fuzzyc2cpg.output.overflowdb.DiffGraphAndKeyPool
import overflowdb.OdbConfig
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import scala.collection.mutable.ListBuffer

class OutputModule extends CpgOutputModule {

  private val cpgBuilders =
    ListBuffer[Either[CpgStruct.Builder, DiffGraphAndKeyPool]]()
  private val odbConfig = OdbConfig.withoutOverflow()
  private val protoToCpg = new ProtoToCpg(odbConfig)

  def getInternalGraph: Cpg = new Cpg(protoToCpg.graph)

  override def persistCpg(builder: CpgStruct.Builder): Unit = {
    cpgBuilders synchronized {
      cpgBuilders.addOne(Left(builder))
    }
  }

  @throws[IOException]
  override def persistCpg(diffGraph: DiffGraph, keyPool: KeyPool): Unit = {
    cpgBuilders synchronized {
      cpgBuilders.addOne(Right(DiffGraphAndKeyPool(diffGraph, keyPool)))
    }
  }

  def persist(): Unit = {
    cpgBuilders.foreach {
      case Left(b) =>
        protoToCpg.addNodes(b.getNodeList)
        protoToCpg.addEdges(b.getEdgeList)
      case Right(DiffGraphAndKeyPool(diffGraph, keyPool)) =>
        DiffGraph.Applier.applyDiff(diffGraph, protoToCpg.graph, undoable = false, Some(keyPool))
    }
  }
}
