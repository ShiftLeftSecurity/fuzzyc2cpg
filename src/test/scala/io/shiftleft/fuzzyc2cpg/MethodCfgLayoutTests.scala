package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes, Operators}
import org.scalatest.{Matchers, WordSpec}

class MethodCfgLayoutTests extends WordSpec with Matchers with TraversalUtils {
  val fixture = CpgTestFixture("methodcfglayout")

  implicit class VertexListWrapper(vertexList: List[Vertex]) {
    def expandCfg(): List[Vertex] = {
      vertexList.flatMap(_.start.out(EdgeTypes.CFG).l)
    }

    def checkForSingleProperty(label: String, property: Key[String], value: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
      vertexList.head.value2(property) shouldBe value
    }

    def checkForSingle(label: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
    }
  }

  "CFG layout" should {
    "be correct for decl assignment in method1" in {
      var result = getMethod("method1").expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeys.NAME, "x")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.LITERAL, NodeKeys.CODE, "1")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.CALL, NodeKeys.NAME, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.METHOD_RETURN)
    }

    "be correct for nested expression in method2" in {
      var result = getMethod("method2").expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeys.NAME, "x")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeys.NAME, "y")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeys.NAME, "z")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.CALL, NodeKeys.NAME, Operators.addition)

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.CALL, NodeKeys.NAME, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.METHOD_RETURN)
    }

    "be correct in method3" in {
      var result = getMethod("method3").expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeys.NAME, "x")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.LITERAL, NodeKeys.CODE, "1")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.RETURN, NodeKeys.CODE, "return 1")
    }
  }

}
