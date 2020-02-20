package io.shiftleft.fuzzyc2cpg.output.overflowdb

import java.util.concurrent.BlockingQueue

import better.files.File
import io.shiftleft.codepropertygraph.cpgloading.{CpgLoader, ProtoToCpg}
import io.shiftleft.overflowdb.{OdbConfig, OdbGraph}
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import org.slf4j.LoggerFactory

class OverflowDbWriter(outputPath: String, queue: BlockingQueue[CpgStruct.Builder]) extends Runnable {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(): Unit = {

    val outFile = File(outputPath)
    if (outputPath != "" && outFile.exists) {
      logger.info("Output file exists, removing: " + outputPath)
      outFile.delete()
    }
    val odbConfig = OdbConfig.withDefaults.withStorageLocation(outputPath)
    val protoToCpg = new ProtoToCpg(odbConfig)
    try {
      var terminate = false;
      while (!terminate) {
        val subCpg = queue.take()
        if (subCpg.getNodeCount == 1 && subCpg.getNode(0).getKey == -1) {
          terminate = true
        } else {
          protoToCpg.addNodes(subCpg.getNodeList)
          protoToCpg.addEdges(subCpg.getEdgeList)
        }
      }

    } catch {
      case _: InterruptedException => logger.warn("Interrupted OverflowDbWriter.")
    } finally {
      val cpg = protoToCpg.build
      CpgLoader.createIndexes(cpg)
      cpg.graph.asInstanceOf[OdbGraph].close()
    }
  }
}
