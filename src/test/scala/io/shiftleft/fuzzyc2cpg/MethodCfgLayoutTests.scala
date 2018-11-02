package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodCfgLayoutTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodcfglayout")

  private def method(name: String): List[Vertex] = {
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

    def checkForSingleNode(label: String, name: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
      vertexList.head.value2(NodeKeys.NAME) shouldBe name
    }

    def checkForSingleNode(label: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
    }
  }

  "CFG layout" should {
    "be correct in method1" in {
      var result = method("method1").expandCfg()
      result.checkForSingleNode(NodeTypes.IDENTIFIER, "x")

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.LITERAL, "1")

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.CALL, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.METHOD_RETURN)
    }

    "be correct in method2" in {
      var result = method("method2").expandCfg()
      result.checkForSingleNode(NodeTypes.IDENTIFIER, "x")

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.IDENTIFIER, "y")

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.IDENTIFIER, "z")

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.CALL, Operators.addition)

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.CALL, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingleNode(NodeTypes.METHOD_RETURN)
    }
  }

}
