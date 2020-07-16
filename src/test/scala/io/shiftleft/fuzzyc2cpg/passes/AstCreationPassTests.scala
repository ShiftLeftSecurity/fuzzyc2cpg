package io.shiftleft.fuzzyc2cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.IntervalKeyPool
import io.shiftleft.semanticcpg.language._
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.codepropertygraph.generated.{Operators, nodes}

class AstCreationPassTests extends WordSpec with Matchers {

  "Method AST layout" should {

    "be correct for empty method" in Fixture("void method(int x) { }") { cpg =>
      cpg.method.astChildren.orderBy(_.order).l match {
        case List(ret: nodes.MethodReturn, param: nodes.MethodParameterIn, _: nodes.Block) =>
          ret.typeFullName shouldBe "void"
          param.typeFullName shouldBe "int"
          param.name shouldBe "x"
        case _ => fail
      }
    }

    "be correct for decl assignment" in Fixture("""
        |void method() {
        |  int local = 1;
        |}
        |""".stripMargin) { cpg =>
      cpg.method.name("method").block.astChildren.orderBy(_.order).l match {
        case List(local: nodes.Local, call: nodes.Call) =>
          local.name shouldBe "local"
          local.typeFullName shouldBe "int"
          call.name shouldBe Operators.assignment
          call.start.astChildren.l match {
            case List(identifier: nodes.Identifier, literal: nodes.Literal) =>
              identifier.name shouldBe "local"
              identifier.typeFullName shouldBe "int"
              identifier.order shouldBe 1
              identifier.argumentIndex shouldBe 1
              literal.code shouldBe "1"
              literal.typeFullName shouldBe "int"
              literal.order shouldBe 2
              literal.argumentIndex shouldBe 2
          }
        case _ => fail
      }
    }

    "be correct for decl assignment with identifier on the right" in
      Fixture("""
              |void method(int x) {
              |  int local = x;
              |}""".stripMargin) { cpg =>
        cpg.method.block.astChildren.orderBy(_.order).assignments.source.l match {
          case List(identifier: nodes.Identifier) =>
            identifier.code shouldBe "x"
            identifier.typeFullName shouldBe "int"
            identifier.order shouldBe 2
            identifier.argumentIndex shouldBe 2
          case _ => fail
        }
      }

    "be correct for decl assignment of multiple locals" in
      Fixture("""
              |void method(int x, int y) {
              |  int local = x, local2 = y;
              |}""".stripMargin) { cpg =>
        cpg.local.l match {
          case List(local1, local2) =>
            local1.name shouldBe "local"
            local2.name shouldBe "local2"
          case _ => fail
        }
      }

  }

  "AstCreationPass" should {

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
