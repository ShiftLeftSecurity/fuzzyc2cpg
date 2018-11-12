package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes}
import io.shiftleft.fuzzyc2cpg.astnew.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeProperty.NodeProperty
import io.shiftleft.fuzzyc2cpg.astnew.{AstToCpgConverter, CpgAdapter}
import io.shiftleft.fuzzyc2cpg.parsetreetoast.FunctionContentTestUtil
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.scalatest.{Matchers, WordSpec}

class MethodAstTests extends WordSpec with Matchers {

  private class GraphAdapter(graph: ScalaGraph) extends CpgAdapter[Vertex, Vertex] {
    override def createNodeBuilder(kind: NodeKind): Vertex = {
      graph.addVertex(kind.toString)
    }

    override def createNode(vertex: Vertex): Vertex = {
      vertex
    }

    override def addProperty(vertex: Vertex, property: NodeProperty, value: String): Unit = {
      vertex.property(property.toString, value)
    }

    override def addProperty(vertex: Vertex, property: NodeProperty, value: Int): Unit = {
      vertex.property(property.toString, value)
    }

    override def addEdge(edgeKind: EdgeKind, dstNode: Vertex, srcNode: Vertex): Unit = {
      srcNode.addEdge(edgeKind.toString, dstNode)
    }
  }

  private implicit class VertexListWrapper(vertexList: List[Vertex]) {
    def expandAst(filterLabels: String*): List[Vertex] = {
      if (filterLabels.nonEmpty) {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).hasLabel(filterLabels.head, filterLabels.tail: _*).l)
      } else {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).l)
      }
    }

    def filterOrder(order: Int): List[Vertex] = {
      vertexList.filter(_.value2(NodeKeys.ORDER) == order)
    }

    def checkForSingle[T](propertyName: Key[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.value2(propertyName) shouldBe value
    }

    def checkForSingle(): Unit = {
      vertexList.size shouldBe 1
    }

    def check[A](count: Int,
                 mapFunc: Vertex => A,
                 expectations: A*): Unit = {
      vertexList.size shouldBe count
      vertexList.map(mapFunc).toSet shouldBe expectations.toSet
    }

  }

  private class Fixture(code: String) {
    private val astRoot = FunctionContentTestUtil.parseAndWalk(code)

    private val graph = TinkerGraph.open()
    private val cpgAdapter = new GraphAdapter(graph)
    private val astToProtoConverter = new AstToCpgConverter("codeFromString", cpgAdapter)
    astToProtoConverter.convert(astRoot)

    def getMethod(name: String): List[Vertex] = {
      val result = graph.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> name)
        .l

      result.size shouldBe 1
      result
    }
  }

  "foo" should {
    "bar" in new Fixture("void method1(){}") {
      val method = getMethod("method1")
      method.expandAst(NodeTypes.BLOCK).checkForSingle()
    }
  }
}
