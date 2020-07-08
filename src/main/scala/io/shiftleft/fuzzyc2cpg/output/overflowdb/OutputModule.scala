package io.shiftleft.fuzzyc2cpg.output.overflowdb

import java.util.concurrent.BlockingQueue

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule
import io.shiftleft.proto.cpg.Cpg.CpgStruct

class OutputModule(queue: BlockingQueue[CpgStruct.Builder]) extends CpgOutputModule {

  override def setOutputIdentifier(identifier: String): Unit = {}

  override def persistCpg(cpg: CpgStruct.Builder): Unit = queue.add(cpg)

}
