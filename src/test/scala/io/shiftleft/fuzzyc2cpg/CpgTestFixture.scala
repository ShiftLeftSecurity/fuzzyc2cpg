package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.output.inmemory.OutputModuleFactory

case class CpgTestFixture(projectName: String) {

  val cpg: Cpg = {
    val dirName = String.format("src/test/resources/testcode/%s", projectName)
    val inmemoryOutputFactory = new OutputModuleFactory()
    val fuzzyc2Cpg = new FuzzyC2Cpg(inmemoryOutputFactory)
    fuzzyc2Cpg.runAndOutput(List(dirName).toArray)
    inmemoryOutputFactory.getInternalGraph
  }

  def V = cpg.scalaGraph.V

}
