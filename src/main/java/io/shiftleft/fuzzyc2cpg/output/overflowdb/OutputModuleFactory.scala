package io.shiftleft.fuzzyc2cpg.output.overflowdb

import java.util.concurrent.BlockingQueue

import io.shiftleft.fuzzyc2cpg.output.{CpgOutputModule, CpgOutputModuleFactory}
import io.shiftleft.proto.cpg.Cpg
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import org.slf4j.LoggerFactory

class OutputModuleFactory(outputPath : String, queue : BlockingQueue[CpgStruct.Builder]) extends CpgOutputModuleFactory {

  private val logger = LoggerFactory.getLogger(getClass)
  private val writer = new OverflowDbWriter(outputPath, queue)
  new Thread(writer).run()

  override def create(): CpgOutputModule = new OutputModule(queue)

  override def persist(): Unit = {
    try {
      val endMarker = Cpg.CpgStruct.newBuilder().addNode(Cpg.CpgStruct.Node.newBuilder().setKey(-1))
      queue.put(endMarker)
    } catch {
      case _ : InterruptedException => logger.warn("Interrupted during persist operation")
    }
  }
}
