package io.shiftleft.fuzzyc2cpg.output.overflowdb

import java.util.concurrent.BlockingQueue

import better.files.File
import io.shiftleft.codepropertygraph.generated.nodes
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, ProtoToCpg}
import io.shiftleft.passes.{DiffGraph, KeyPool}
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import org.slf4j.LoggerFactory
import overflowdb.OdbConfig

case class DiffGraphAndKeyPool(diffGraph: DiffGraph, keyPool: KeyPool)

class OverflowDbWriter(outputPath: String, queue: BlockingQueue[Either[CpgStruct.Builder, DiffGraphAndKeyPool]])
    extends Runnable {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(): Unit = {

    val outFile = File(outputPath)
    if (outputPath != "" && outFile.exists) {
      logger.info("Output file exists, removing: " + outputPath)
      outFile.delete()
    }
    val odbConfig = OdbConfig.withDefaults.withStorageLocation(outputPath)
    val protoToCpg = new ProtoToCpg(odbConfig)
    val graph = protoToCpg.graph
    try {
      var terminate = false;
      while (!terminate) {
        val subCpg = queue.take()
        if (isTerminator(subCpg)) {
          terminate = true
        } else {
          mergeIntoCpg(subCpg)
        }
      }

    } catch {
      case _: InterruptedException => logger.warn("Interrupted OverflowDbWriter.")
    } finally {
      val cpg = protoToCpg.build
      CpgLoader.createIndexes(cpg)
      cpg.graph.close()
    }

    def isTerminator(subCpg: Either[CpgStruct.Builder, DiffGraphAndKeyPool]): Boolean = {
      subCpg match {
        case Left(proto) => proto.getNodeCount == 1 && proto.getNode(0).getKey == -1
        case Right(DiffGraphAndKeyPool(diffGraph, _)) =>
          diffGraph.nodes.toList match {
            case (unk: nodes.NewUnknown) :: Nil =>
              unk.parserTypeName == "terminate"
            case _ => false
          }

      }
    }

    def mergeIntoCpg(subCpg: Either[CpgStruct.Builder, DiffGraphAndKeyPool]): Unit = {
      subCpg match {
        case Left(proto) => {
          protoToCpg.addNodes(proto.getNodeList)
          protoToCpg.addEdges(proto.getEdgeList)
        }
        case Right(DiffGraphAndKeyPool(diffGraph, keyPool)) => {
          DiffGraph.Applier.applyDiff(diffGraph, graph, undoable = false, Some(keyPool))
        }
      }
    }
  }

}
