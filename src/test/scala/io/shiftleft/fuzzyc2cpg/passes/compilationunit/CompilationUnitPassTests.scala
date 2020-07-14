package io.shiftleft.fuzzyc2cpg.passes.compilationunit

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.passes.FileAndNamespaceBlockPass
import io.shiftleft.passes.IntervalKeyPool
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.semanticcpg.language._

object Fixture {

  def apply(f: Cpg => Unit): Unit = {
    File.usingTemporaryDirectory("fuzzyctest") { dir =>
      val file1 = (dir / "file1.c")
      val file2 = (dir / "file2.c")
      file1.write("""
          | // A comment
          |""".stripMargin)
      file2.write("""
          |
          |""".stripMargin)

      val cpg = Cpg.emptyCpg
      val keyPools = Iterator(new IntervalKeyPool(1001, 2000), new IntervalKeyPool(2001, 3000))
      val filenames = List(file1.path.toAbsolutePath.toString, file2.path.toAbsolutePath.toString)
      new FileAndNamespaceBlockPass(filenames, cpg, Some(new IntervalKeyPool(0, 1000))).createAndApply()
      new CompilationUnitPass(filenames, cpg, Some(keyPools)).createAndApply()
      f(cpg)
    }
  }
}

class CompilationUnitPassTests extends WordSpec with Matchers {

  "CompilationUnitPass" should {

    "a node for the comment" in Fixture { cpg =>
      println(cpg.comment.code.l shouldBe List("// A comment\n"))
    }

  }

}
