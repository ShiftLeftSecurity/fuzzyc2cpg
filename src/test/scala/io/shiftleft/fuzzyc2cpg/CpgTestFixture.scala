package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.outputmodules.InMemoryGraphOutputModule
import io.shiftleft.queryprimitives.steps.starters.Cpg

case class CpgTestFixture(projectName: String) {

  val cpg: Cpg = {
    val dirName = String.format("src/test/resources/testcode/%s", projectName)
    val inmemoryOutputModule = new InMemoryGraphOutputModule()
    val fuzzyc2Cpg = new Fuzzyc2Cpg(inmemoryOutputModule)
    fuzzyc2Cpg.runAndOutput(List(dirName).toArray)
    inmemoryOutputModule.getCpg
  }

}
