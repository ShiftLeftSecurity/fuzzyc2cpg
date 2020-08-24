package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeysOdb, NodeTypes, Operators}
import org.scalatest.{Matchers, WordSpec}
import overflowdb.traversal._
import overflowdb.{Node, PropertyKey}

class MethodCfgLayoutTests extends WordSpec with Matchers with TraversalUtils {
  val fixture = CpgTestFixture("methodcfglayout")

  implicit class VertexListWrapper(vertexList: List[Node]) {
    def expandCfg(): List[Node] = {
      vertexList.flatMap(_.start.out(EdgeTypes.CFG).l)
    }

    def checkForSingleProperty(label: String, propertyKey: PropertyKey[String], value: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
      vertexList.head.property2[String](propertyKey.name) shouldBe value
    }

    def checkForSingle(label: String): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label shouldBe label
    }
  }

  "CFG layout" should {
    "be correct for decl assignment in method1" in {
      var result = getMethod("method1").expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeysOdb.NAME, "x")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.LITERAL, NodeKeysOdb.CODE, "1")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.CALL, NodeKeysOdb.NAME, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.METHOD_RETURN)
    }

    "be correct for nested expression in method2" in {
      var result = getMethod("method2").expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeysOdb.NAME, "x")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeysOdb.NAME, "y")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.IDENTIFIER, NodeKeysOdb.NAME, "z")

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.CALL, NodeKeysOdb.NAME, Operators.addition)

      result = result.expandCfg()
      result.checkForSingleProperty(NodeTypes.CALL, NodeKeysOdb.NAME, Operators.assignment)

      result = result.expandCfg()
      result.checkForSingle(NodeTypes.METHOD_RETURN)
    }
  }

}
