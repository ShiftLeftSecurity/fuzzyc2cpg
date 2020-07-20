package io.shiftleft.fuzzyc2cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.IntervalKeyPool
import io.shiftleft.semanticcpg.language._
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.codepropertygraph.generated.{Operators, nodes}
import scala.jdk.CollectionConverters._

class AstCreationPassTests extends WordSpec with Matchers {

  "Method AST layout" should {

    "be correct for empty method" in Fixture("void method(int x) { }") { cpg =>
      cpg.method.name("method").astChildren.l match {
        case List(param: nodes.MethodParameterIn, _: nodes.Block, ret: nodes.MethodReturn) =>
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
      cpg.method.name("method").block.astChildren.l match {
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
        cpg.method.block.astChildren.assignments.source.l match {
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
        // Note that `cpg.method.local` does not work
        // because it depends on CONTAINS edges which
        // are created by a backend pass in semanticcpg
        // construction.

        cpg.local.orderBy(_.order).l match {
          case List(local1, local2) =>
            local1.name shouldBe "local"
            local1.typeFullName shouldBe "int"
            local2.name shouldBe "local2"
            local2.typeFullName shouldBe "int"
          case _ => fail
        }

        cpg.assignment.orderBy(_.order).l match {
          case List(a1, a2) =>
            List(a1.target.code, a1.source.code) shouldBe List("local", "x")
            List(a2.target.code, a2.source.code) shouldBe List("local2", "y")
          case _ => fail
        }
      }

    "be correct for nested expression" in Fixture("""
        |void method() {
        |  int x;
        |  int y;
        |  int z;
        |
        |  x = y + z;
        |}
      """.stripMargin) { cpg =>
      cpg.local.orderBy(_.order).name.l shouldBe List("x", "y", "z")

      cpg.method.assignments.l match {
        case List(assignment) =>
          assignment.target.code shouldBe "x"
          assignment.source.start.isCall.name.l shouldBe List(Operators.addition)
          assignment.source.start.astChildren.l match {
            case List(id1: nodes.Identifier, id2: nodes.Identifier) =>
              id1.order shouldBe 1
              id1.code shouldBe "y"
              id2.order shouldBe 2
              id2.code shouldBe "z"
          }
        case _ => fail
      }
    }

    "be correct for nested block" in Fixture("""
        |void method() {
        |  int x;
        |  {
        |    int y;
        |  }
        |}
      """.stripMargin) { cpg =>
      cpg.method.name("method").block.astChildren.l match {
        case List(local: nodes.Local, innerBlock: nodes.Block) =>
          local.name shouldBe "x"
          innerBlock.start.astChildren.l match {
            case List(localInBlock: nodes.Local) =>
              localInBlock.name shouldBe "y"
            case _ => fail
          }
        case _ => fail
      }
    }
  }

  "be correct for while-loop" in Fixture("""
                                               |void method(int x) {
                                               |  while (x < 1) {
                                               |    x += 1;
                                               |  }
                                               |}
      """.stripMargin) { cpg =>
    cpg.method.name("method").block.astChildren.isControlStructure.l match {
      case List(controlStruct: nodes.ControlStructure) =>
        controlStruct.code shouldBe "while (x < 1)"
        controlStruct.parserTypeName shouldBe "WhileStatement"
        controlStruct._conditionOut().asScala.toList match {
          case List(cndNode: nodes.Expression) =>
            cndNode.code shouldBe "x < 1"
          case _ => fail
        }
        controlStruct.start.whenTrue.assignments.code.l shouldBe List("x += 1")
      case _ => fail
    }
  }

  "be correct for if" in Fixture("""
                                       |void method(int x) {
                                       |  int y;
                                       |  if (x > 0) {
                                       |    y = 0;
                                       |  }
                                       |}
      """.stripMargin) { cpg =>
    cpg.method.name("method").controlStructure.l match {
      case List(controlStruct: nodes.ControlStructure) =>
        controlStruct.code shouldBe "if (x > 0)"
        controlStruct.parserTypeName shouldBe "IfStatement"
        controlStruct._conditionOut().asScala.toList match {
          case List(cndNode: nodes.Expression) =>
            cndNode.code shouldBe "x > 0"
          case _ => fail
        }
        controlStruct.start.whenTrue.assignments.code.l shouldBe List("y = 0")
      case _ => fail
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
