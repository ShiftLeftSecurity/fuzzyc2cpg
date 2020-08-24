package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeysOdb, NodeTypes}
import org.scalatest.{Matchers, WordSpec}
import overflowdb.traversal._
import overflowdb.{Node, PropertyKey}


class MethodInternalLinkageTests extends WordSpec with Matchers with TraversalUtils {
  val fixture = CpgTestFixture("methodinternallinkage")

  implicit class VertexListWrapper(vertexList: List[Node]) {
    def expandAst(filterLabels: String*): List[Node] = {
      if (filterLabels.nonEmpty) {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).hasLabel(filterLabels: _*).l)
      } else {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).l)
      }
    }

    def expandRef(): List[Node] = {
      vertexList.flatMap(_.start.out(EdgeTypes.REF).l)
    }

    def filterOrder(order: Int): List[Node] = {
        vertexList.to(Traversal).has(NodeKeysOdb.ORDER -> order).l
    }

    def filterName(name: String): List[Node] = {
      vertexList.to(Traversal).has(NodeKeysOdb.NAME -> name).l
    }

    def checkForSingle[T](label: String, propertyKey: PropertyKey[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label() shouldBe label
      vertexList.head.property2[T](propertyKey.name) shouldBe value
    }

    def checkForSingle[T](propertyKey: PropertyKey[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.property2[T](propertyKey.name) shouldBe value
    }
  }

  "REF edges" should {
    "be correct for local x in method1" in {
      val method = getMethod("method1")
      val indentifierX = method.expandAst().expandAst().expandAst(NodeTypes.IDENTIFIER)
      indentifierX.checkForSingle(NodeKeysOdb.NAME, "x")

      val localX = indentifierX.expandRef()
      localX.checkForSingle(NodeTypes.LOCAL, NodeKeysOdb.NAME, "x")
    }

    "be correct for parameter x in method2" in {
      val method = getMethod("method2")
      val indentifierX = method.expandAst().expandAst().expandAst(NodeTypes.IDENTIFIER)
      indentifierX.checkForSingle(NodeKeysOdb.NAME, "x")

      val parameterX = indentifierX.expandRef()
      parameterX.checkForSingle(NodeTypes.METHOD_PARAMETER_IN, NodeKeysOdb.NAME, "x")
    }

    "be correct for all identifiers x, y in method3" in {
      val method = getMethod("method3")
      val outerIdentifierX = method.expandAst().expandAst().filterOrder(3).expandAst(NodeTypes.IDENTIFIER)
      outerIdentifierX.checkForSingle(NodeKeysOdb.NAME, "x")
      val parameterX = outerIdentifierX.expandRef()
      parameterX.checkForSingle(NodeTypes.METHOD_PARAMETER_IN, NodeKeysOdb.NAME, "x")
      val expectedParameterX = method.expandAst(NodeTypes.METHOD_PARAMETER_IN)
      expectedParameterX.checkForSingle(NodeKeysOdb.NAME, "x")
      parameterX shouldBe expectedParameterX

      val outerIdentifierY = method.expandAst().expandAst().filterOrder(4).expandAst(NodeTypes.IDENTIFIER)
      outerIdentifierY.checkForSingle(NodeKeysOdb.NAME, "y")
      val outerLocalY = outerIdentifierY.expandRef()
      outerLocalY.checkForSingle(NodeTypes.LOCAL, NodeKeysOdb.NAME, "y")
      val expectedOuterLocalY = method.expandAst().expandAst(NodeTypes.LOCAL)
      expectedOuterLocalY.checkForSingle(NodeKeysOdb.NAME, "y")
      outerLocalY shouldBe expectedOuterLocalY

      val nestedBlock = method.expandAst().expandAst(NodeTypes.BLOCK)

      val nestedIdentifierX = nestedBlock.expandAst().filterOrder(3).expandAst(NodeTypes.IDENTIFIER)
      nestedIdentifierX.checkForSingle(NodeKeysOdb.NAME, "x")
      val nestedLocalX = nestedIdentifierX.expandRef()
      nestedLocalX.checkForSingle(NodeTypes.LOCAL, NodeKeysOdb.NAME, "x")
      val expectedNestedLocalX = nestedBlock.expandAst(NodeTypes.LOCAL).filterName("x")
      expectedNestedLocalX.checkForSingle(NodeKeysOdb.NAME, "x")
      nestedLocalX shouldBe expectedNestedLocalX

      val nestedIdentifierY = nestedBlock.expandAst().filterOrder(4).expandAst(NodeTypes.IDENTIFIER)
      nestedIdentifierY.checkForSingle(NodeKeysOdb.NAME, "y")
      val nestedLocalY = nestedIdentifierY.expandRef()
      nestedLocalY.checkForSingle(NodeTypes.LOCAL, NodeKeysOdb.NAME, "y")
      val expectedNestedLocalY = nestedBlock.expandAst(NodeTypes.LOCAL).filterName("y")
      expectedNestedLocalY.checkForSingle(NodeKeysOdb.NAME, "y")
      nestedLocalY shouldBe expectedNestedLocalY
    }
  }

}
