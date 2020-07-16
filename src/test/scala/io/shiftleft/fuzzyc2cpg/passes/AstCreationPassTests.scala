package io.shiftleft.fuzzyc2cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.IntervalKeyPool
import io.shiftleft.semanticcpg.language._
import org.scalatest.{Matchers, WordSpec}

class AstCreationPassTests extends WordSpec with Matchers {

  "CompilationUnitPass" should {

    "create a node for the comment" in Fixture("// A comment\n") { cpg =>
      cpg.comment.code.l shouldBe List("// A comment\n")
    }

    "create a TYPE_DECL node for named struct" in Fixture("struct my_struct { };") { cpg =>
      cpg.typeDecl.l match {
        case typeDecl :: Nil =>
          typeDecl.name shouldBe "my_struct"
          typeDecl.fullName shouldBe "my_struct"
          typeDecl.isExternal shouldBe false
        case _ => fail
      }
    }

  }

}

object Fixture {
  def apply(file1Code: String, file2Code: String = "")(f: Cpg => Unit): Unit = {
    File.usingTemporaryDirectory("fuzzyctest") { dir =>
      val file1 = (dir / "file1.c")
      val file2 = (dir / "file2.c")
      file1.write(file1Code)
      file2.write(file2Code)

      val cpg = Cpg.emptyCpg
      val keyPools = Iterator(new IntervalKeyPool(1001, 2000), new IntervalKeyPool(2001, 3000))
      val filenames = List(file1.path.toAbsolutePath.toString, file2.path.toAbsolutePath.toString)
      new FileAndNamespaceBlockPass(filenames, cpg, Some(new IntervalKeyPool(0, 1000))).createAndApply()
      new AstCreationPass(filenames, cpg, Some(keyPools)).createAndApply()
      f(cpg)
    }
  }
}
