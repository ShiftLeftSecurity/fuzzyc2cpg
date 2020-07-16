package io.shiftleft.fuzzyc2cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.IntervalKeyPool
import org.scalatest.{Matchers, WordSpec}

class CfgCreationPassTests extends WordSpec with Matchers {

  "CfgCreationPass" should {

    "foo" in CfgFixture("int x = 1;") { cpg =>
      }

  }

}

object CfgFixture {
  def apply(file1Code: String)(f: Cpg => Unit): Unit = {
    File.usingTemporaryDirectory("fuzzyctest") { dir =>
      val file1 = (dir / "file1.c")
      file1.write(s"func() { $file1Code }")
      val cpg = Cpg.emptyCpg
      val keyPoolFile1 = new IntervalKeyPool(1001, 2000)
      val keyPools = Iterator(keyPoolFile1)
      val filenames = List(file1.path.toAbsolutePath.toString)
      new FileAndNamespaceBlockPass(filenames, cpg, Some(new IntervalKeyPool(0, 1000))).createAndApply()
      new AstCreationPass(filenames, cpg, Some(keyPools)).createAndApply()
      new CfgCreationPass(cpg, Some(keyPools)).createAndApply()
      f(cpg)
    }
  }
}
