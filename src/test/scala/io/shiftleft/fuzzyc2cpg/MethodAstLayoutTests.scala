package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodAstLayoutTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodastlayout")

  "AST layout" should {
    "have one BLOCK below METHOD in method1" in {
      val result = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> "method1")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.BLOCK)
        .l

      result.size shouldBe 1
    }

    "have LOCAL below BLOCK in method2" in {
      val result = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> "method2")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.BLOCK)
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.LOCAL)
        .l

      result.size shouldBe 1
      result.head.value2(NodeKeys.NAME) shouldBe "local"
    }

    "have an assignment in method2" in {
      val result = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> "method2")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.BLOCK)
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.CALL)
        .l

      result.size shouldBe 1
      result.head.value2(NodeKeys.NAME) shouldBe Operators.assignment
    }

    "have two arguments for assignment in method2" in {
      val result = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> "method2")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.BLOCK)
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.CALL)
        .out(EdgeTypes.AST)
        .l

      result.size shouldBe 2
      result.toSet.map { arg: Vertex =>
        (arg.label,
          arg.value2(NodeKeys.CODE),
          arg.value2(NodeKeys.ORDER),
          arg.value2(NodeKeys.ARGUMENT_INDEX))
      } shouldBe
      Set((NodeTypes.IDENTIFIER, "local", 1, 1),
        (NodeTypes.LITERAL, "1", 2, 2))
    }
  }

  "have 3 LOCAL below BLOCK in method3" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method3")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.LOCAL)
      .l

    result.size shouldBe 3
    result.toSet.map { local: Vertex =>
      local.value2(NodeKeys.NAME)
    } shouldBe
    Set("x", "y", "z")
  }

  "have addition below assignment in method3" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method3")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.CALL)
      .has(NodeKeys.NAME -> Operators.assignment)
      .out(EdgeTypes.AST)
      .has(NodeKeys.ORDER -> 2)
      .hasLabel(NodeTypes.CALL)
      .has(NodeKeys.NAME -> Operators.addition)
      .l

    result.size shouldBe 1
  }

  "have two arguments below addition in method3" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method3")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.CALL)
      .has(NodeKeys.NAME -> Operators.assignment)
      .out(EdgeTypes.AST)
      .has(NodeKeys.ORDER -> 2)
      .hasLabel(NodeTypes.CALL)
      .has(NodeKeys.NAME -> Operators.addition)
      .out(EdgeTypes.AST)
      .l

    result.size shouldBe 2
    result.toSet.map { arg: Vertex =>
      (arg.label,
        arg.value2(NodeKeys.CODE),
        arg.value2(NodeKeys.ORDER),
        arg.value2(NodeKeys.ARGUMENT_INDEX))
    } shouldBe
      Set((NodeTypes.IDENTIFIER, "y", 1, 1),
        (NodeTypes.IDENTIFIER, "z", 2, 2))
  }

  "have LOCAL x below BLOCK in method4" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method4")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.LOCAL)
      .l

    result.size shouldBe 1
    result.head.value2(NodeKeys.NAME) shouldBe "x"
  }

  "have LOCAL y below nested BLOCK in method4" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method4")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.LOCAL)
      .l

    result.size shouldBe 1
    result.head.value2(NodeKeys.NAME) shouldBe "y"
  }

  "have UNKNOWN 'whileStatment' in method5" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method5")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.UNKNOWN)
      .l

    result.size shouldBe 1
  }

  "have CALL to operator condition below 'whileStatment' in method5" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method5")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.UNKNOWN)
      .has(NodeKeys.PARSER_TYPE_NAME -> "WhileStatement")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.CALL)
      .has(NodeKeys.NAME -> Operators.lessThan)
      .l

    result.size shouldBe 1
  }

   "have BLOCK below 'whileStatment' in method5" in {
     val result = fixture.V
       .hasLabel(NodeTypes.METHOD)
       .has(NodeKeys.NAME -> "method5")
       .out(EdgeTypes.AST)
       .hasLabel(NodeTypes.BLOCK)
       .out(EdgeTypes.AST)
       .hasLabel(NodeTypes.UNKNOWN)
       .has(NodeKeys.PARSER_TYPE_NAME -> "WhileStatement")
       .out(EdgeTypes.AST)
       .hasLabel(NodeTypes.BLOCK)
       .l

     result.size shouldBe 1
   }

  "have CALL to '+=' below BLOCK in method5" in {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> "method5")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.UNKNOWN)
      .has(NodeKeys.PARSER_TYPE_NAME -> "WhileStatement")
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.BLOCK)
      .out(EdgeTypes.AST)
      .hasLabel(NodeTypes.CALL)
      .has(NodeKeys.NAME -> Operators.assignmentPlus)
      .has(NodeKeys.ORDER -> 1)
      .l

    result.size shouldBe 1
  }

}
