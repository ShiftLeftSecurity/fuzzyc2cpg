package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, EvaluationStrategies, NodeKeys, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodHeaderTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodheader")

  "Method header" should {

    "have correct METHOD node for method foo" in {
      val methods = fixture.V.hasLabel(NodeTypes.METHOD).has(NodeKeys.NAME -> "foo").l
      methods.size shouldBe 1
      methods.head.value2(NodeKeys.IS_EXTERNAL) shouldBe false
      methods.head.value2(NodeKeys.FULL_NAME) shouldBe "foo"
      methods.head.value2(NodeKeys.SIGNATURE) shouldBe "int(int,int)"
      methods.head.value2(NodeKeys.LINE_NUMBER) shouldBe 1
      methods.head.value2(NodeKeys.COLUMN_NUMBER) shouldBe 0
    }

    "have correct METHOD_PARAMETER_IN nodes for method foo" in {
      val parameters = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> "foo")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.METHOD_PARAMETER_IN)
        .l

      parameters.size shouldBe 2
      val param1Option = parameters.find(_.value2(NodeKeys.ORDER) == 1)
      param1Option.isDefined shouldBe true
      param1Option.get.value2(NodeKeys.CODE) shouldBe "int x"
      param1Option.get.value2(NodeKeys.NAME) shouldBe "x"
      param1Option.get.value2(NodeKeys.EVALUATION_STRATEGY) shouldBe EvaluationStrategies.BY_VALUE
      param1Option.get.value2(NodeKeys.LINE_NUMBER) shouldBe 1
      param1Option.get.value2(NodeKeys.COLUMN_NUMBER) shouldBe 8

      val param2Option = parameters.find(_.value2(NodeKeys.ORDER) == 2)
      param2Option.isDefined shouldBe true
      param2Option.isDefined shouldBe true
      param2Option.get.value2(NodeKeys.CODE) shouldBe "int y"
      param2Option.get.value2(NodeKeys.NAME) shouldBe "y"
      param2Option.get.value2(NodeKeys.EVALUATION_STRATEGY) shouldBe EvaluationStrategies.BY_VALUE
      param2Option.get.value2(NodeKeys.LINE_NUMBER) shouldBe 1
      param2Option.get.value2(NodeKeys.COLUMN_NUMBER) shouldBe 15
    }

    "have correct METHOD_RETURN node for method foo" in {
      val methodReturn = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> "foo")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.METHOD_RETURN)
        .l

      methodReturn.size shouldBe 1
      methodReturn.head.value2(NodeKeys.CODE) shouldBe "RET"
      methodReturn.head.value2(NodeKeys.EVALUATION_STRATEGY) shouldBe EvaluationStrategies.BY_VALUE
      methodReturn.head.value2(NodeKeys.LINE_NUMBER) shouldBe 1
      methodReturn.head.value2(NodeKeys.COLUMN_NUMBER) shouldBe 0
    }

  }

}
