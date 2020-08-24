package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated._
import org.scalatest.{Matchers, WordSpec}

class MethodHeaderTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodheader")

  "Method header" should {

    "have correct METHOD node for method foo" in {
      val methods = fixture.traversalSource.label(NodeTypes.METHOD).has(NodeKeysOdb.NAME -> "foo").l
      methods.size shouldBe 1
      methods.head.property2[Boolean](NodeKeyNames.IS_EXTERNAL) shouldBe false
      methods.head.property2[String](NodeKeyNames.FULL_NAME) shouldBe "foo"
      methods.head.property2[String](NodeKeyNames.SIGNATURE) shouldBe "int foo (int,int)"
      methods.head.property2[Int](NodeKeyNames.LINE_NUMBER) shouldBe 1
      methods.head.property2[Int](NodeKeyNames.COLUMN_NUMBER) shouldBe 0
      methods.head.property2[Int](NodeKeyNames.LINE_NUMBER_END) shouldBe 3
      methods.head.property2[Int](NodeKeyNames.COLUMN_NUMBER_END) shouldBe 0
      methods.head.property2[String](NodeKeyNames.CODE) shouldBe "foo (int x,int y)"
    }

    "have correct METHOD_PARAMETER_IN nodes for method foo" in {
      val parameters = fixture
        .traversalSource
        .label(NodeTypes.METHOD)
        .has(NodeKeysOdb.NAME -> "foo")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.METHOD_PARAMETER_IN)
        .l

      parameters.size shouldBe 2
      val param1Option = parameters.find(_.property2[Int](NodeKeyNames.ORDER) == 1)
      param1Option.isDefined shouldBe true
      param1Option.get.property2[String](NodeKeyNames.CODE) shouldBe "int x"
      param1Option.get.property2[String](NodeKeyNames.NAME) shouldBe "x"
      param1Option.get.property2[String](NodeKeyNames.EVALUATION_STRATEGY) shouldBe EvaluationStrategies.BY_VALUE
      param1Option.get.property2[Int](NodeKeyNames.LINE_NUMBER) shouldBe 1
      param1Option.get.property2[Int](NodeKeyNames.COLUMN_NUMBER) shouldBe 8

      val param2Option = parameters.find(_.property2(NodeKeyNames.ORDER) == 2)
      param2Option.isDefined shouldBe true
      param2Option.isDefined shouldBe true
      param2Option.get.property2[String](NodeKeyNames.CODE) shouldBe "int y"
      param2Option.get.property2[String](NodeKeyNames.NAME) shouldBe "y"
      param2Option.get.property2[String](NodeKeyNames.EVALUATION_STRATEGY) shouldBe EvaluationStrategies.BY_VALUE
      param2Option.get.property2[Int](NodeKeyNames.LINE_NUMBER) shouldBe 1
      param2Option.get.property2[Int](NodeKeyNames.COLUMN_NUMBER) shouldBe 15
    }

    "have correct METHOD_RETURN node for method foo" in {
      val methodReturn = fixture
        .traversalSource
        .label(NodeTypes.METHOD)
        .has(NodeKeysOdb.NAME -> "foo")
        .out(EdgeTypes.AST)
        .hasLabel(NodeTypes.METHOD_RETURN)
        .l

      methodReturn.size shouldBe 1
      methodReturn.head.property2[String](NodeKeyNames.CODE) shouldBe "RET"
      methodReturn.head.property2[String](NodeKeyNames.EVALUATION_STRATEGY) shouldBe EvaluationStrategies.BY_VALUE
      methodReturn.head.property2[Int](NodeKeyNames.LINE_NUMBER) shouldBe 1
      methodReturn.head.property2[Int](NodeKeyNames.COLUMN_NUMBER) shouldBe 0
    }

  }

}
