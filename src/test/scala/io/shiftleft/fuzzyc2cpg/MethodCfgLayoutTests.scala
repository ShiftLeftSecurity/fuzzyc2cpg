package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodCfgLayoutTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodcfglayout")

  private def getMethod(name: String): List[Vertex] = {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> name)
      .l

    result.size shouldBe 1
    result
  }

  implicit class VertexListWrapper(vertexList: List[Vertex]) {
    def expandCfg(): List[Vertex] = {
      vertexList.flatMap(_.start.out(EdgeTypes.CFG).l)
    }

    def checkForSingle(label: String, name: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
      vertexList.head.value2(NodeKeys.NAME) shouldBe name
    }

    def checkForSingle(label: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
    }
  }

  "CFG layout" should {
    "be correct for decl assignment in method1" in {
      var result = getMethod("method1").expandCfg()
      result.checkForSingle(NodeTypes.IDENTIFIER, "x")

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.LITERAL, "1")

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.CALL, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.METHOD_RETURN)
    }

    "be correct for nested expression in method2" in {
      var result = getMethod("method2").expandCfg()
      result.checkForSingle(NodeTypes.IDENTIFIER, "x")

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.IDENTIFIER, "y")

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.IDENTIFIER, "z")

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.CALL, Operators.addition)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.CALL, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.METHOD_RETURN)
    }
  }

  "be correct for while in method3" in {
    var result = getMethod("method3").expandCfg()
    result.checkForSingle(NodeTypes.IDENTIFIER, "x")

    result = result.expandCfg()
    result.checkForSingle(NodeTypes.LITERAL, "1")

    result = result.expandCfg()
    result.checkForSingle(NodeTypes.CALL, Operators.lessThan)
  }

}
