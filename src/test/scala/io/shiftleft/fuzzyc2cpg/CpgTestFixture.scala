package io.shiftleft.fuzzyc2cpg

import gremlin.scala.GraphAsScala
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.output.inmemory.OutputModuleFactory
import io.shiftleft.fuzzyc2cpg.passes.{AstCreationPass, CMetaDataPass, CfgCreationPass}
import io.shiftleft.passes.IntervalKeyPool
import io.shiftleft.semanticcpg.language._

case class CpgTestFixture(projectName: String) {

  val cpg: Cpg = Cpg.emptyCpg
  val dirName = String.format("src/test/resources/testcode/%s", projectName)
  val keyPoolFile1 = new IntervalKeyPool(1001, 2000)
  val cfgKeyPool = new IntervalKeyPool(2001, 3000)
  val filenames = SourceFiles.determine(Set(dirName), Set(".c"))

  new CMetaDataPass(cpg).createAndApply()
  new AstCreationPass(filenames, cpg, keyPoolFile1).createAndApply()
  if (cpg.method.size > 0) {
    new CfgCreationPass(cpg, cfgKeyPool).createAndApply()
  }


  def V = cpg.graph.asScala.V

}
