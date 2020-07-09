package io.shiftleft.fuzzyc2cpg.output.inmemory

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory

class OutputModuleFactory extends CpgOutputModuleFactory {
  private var outputModule: OutputModule = _

  override def create(): OutputModule = {
    if (outputModule == null) outputModule = new OutputModule()
    outputModule
  }

  /**
    * An internal representation of the graph.
    *
    * @return the internally constructed graph
    */
  def getInternalGraph: Cpg = outputModule.getInternalGraph

  override def persist(): Unit = if (outputModule != null) outputModule.persist()
}
