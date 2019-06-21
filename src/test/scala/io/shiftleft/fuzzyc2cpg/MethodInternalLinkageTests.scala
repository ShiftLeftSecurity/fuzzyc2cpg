package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodInternalLinkageTests extends WordSpec with Matchers with TraversalUtils {
  val fixture = CpgTestFixture("methodinternallinkage")

  implicit class VertexListWrapper(vertexList: List[Vertex]) {
    def expandAst(filterLabels: String*): List[Vertex] = {
      if (filterLabels.nonEmpty) {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).hasLabel(filterLabels.head, filterLabels.tail: _*).l)
      } else {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).l)
      }
    }

    def expandRef(): List[Vertex] = {
      vertexList.flatMap(_.start.out(EdgeTypes.REF).l)
    }

    def filterOrder(order: Int): List[Vertex] = {
      vertexList.filter(_.valueOption(NodeKeys.ORDER).getOrElse(-1) == order)
    }

    def filterName(name: String): List[Vertex] = {
      vertexList.filter(_.valueOption(NodeKeys.NAME).getOrElse("") == name)
    }

    def checkForSingle[T](label: String, propertyName: Key[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.label() shouldBe label
      vertexList.head.value2(propertyName) shouldBe value
    }

    def checkForSingle[T](propertyName: Key[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.value2(propertyName) shouldBe value
    }
  }

  "REF edges" should {
    "be correct for local x in method1" in {
      val method = getMethod("method1")
      val indentifierX = method.expandAst().expandAst().expandAst(NodeTypes.IDENTIFIER)
      indentifierX.checkForSingle(NodeKeys.NAME, "x")

      val localX = indentifierX.expandRef()
      localX.checkForSingle(NodeTypes.LOCAL, NodeKeys.NAME, "x")
    }

    "be correct for parameter x in method2" in {
      val method = getMethod("method2")
      val indentifierX = method.expandAst().expandAst().expandAst(NodeTypes.IDENTIFIER)
      indentifierX.checkForSingle(NodeKeys.NAME, "x")

      val parameterX = indentifierX.expandRef()
      parameterX.checkForSingle(NodeTypes.METHOD_PARAMETER_IN, NodeKeys.NAME, "x")
    }

    "be correct for all indentifiers x, y in method3" in {
      val method = getMethod("method3")

      val outerIndentifierX = method.expandAst().expandAst().filterOrder(2).expandAst(NodeTypes.IDENTIFIER)
      outerIndentifierX.checkForSingle(NodeKeys.NAME, "x")
      val parameterX = outerIndentifierX.expandRef()
      parameterX.checkForSingle(NodeTypes.METHOD_PARAMETER_IN, NodeKeys.NAME, "x")
      val expectedParamterX = method.expandAst(NodeTypes.METHOD_PARAMETER_IN)
      expectedParamterX.checkForSingle(NodeKeys.NAME, "x")
      parameterX shouldBe expectedParamterX

      val outerIndentifierY = method.expandAst().expandAst().filterOrder(3).expandAst(NodeTypes.IDENTIFIER)
      outerIndentifierY.checkForSingle(NodeKeys.NAME, "y")
      val outerLocalY = outerIndentifierY.expandRef()
      outerLocalY.checkForSingle(NodeTypes.LOCAL, NodeKeys.NAME, "y")
      val expectedOuterLocalY = method.expandAst().expandAst(NodeTypes.LOCAL)
      expectedOuterLocalY.checkForSingle(NodeKeys.NAME, "y")
      outerLocalY shouldBe expectedOuterLocalY

      val nestedBlock = method.expandAst().expandAst(NodeTypes.BLOCK)

      val nestedIdentifierX = nestedBlock.expandAst().filterOrder(1).expandAst(NodeTypes.IDENTIFIER)
      nestedIdentifierX.checkForSingle(NodeKeys.NAME, "x")
      val nestedLocalX = nestedIdentifierX.expandRef()
      nestedLocalX.checkForSingle(NodeTypes.LOCAL, NodeKeys.NAME, "x")
      val expectedNestedLocalX = nestedBlock.expandAst(NodeTypes.LOCAL).filterName("x")
      expectedNestedLocalX.checkForSingle(NodeKeys.NAME, "x")
      nestedLocalX shouldBe expectedNestedLocalX

      val nestedIdentifierY = nestedBlock.expandAst().filterOrder(2).expandAst(NodeTypes.IDENTIFIER)
      nestedIdentifierY.checkForSingle(NodeKeys.NAME, "y")
      val nestedLocalY = nestedIdentifierY.expandRef()
      nestedLocalY.checkForSingle(NodeTypes.LOCAL, NodeKeys.NAME, "y")
      val expectedNestedLocalY = nestedBlock.expandAst(NodeTypes.LOCAL).filterName("y")
      expectedNestedLocalY.checkForSingle(NodeKeys.NAME, "y")
      nestedLocalY shouldBe expectedNestedLocalY
    }
  }

}
