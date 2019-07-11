package io.shiftleft.fuzzyc2cpg.astnew

import io.shiftleft.fuzzyc2cpg.IdPool
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.{Edge, Node}
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.astnew.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeProperty.NodeProperty

class ProtoCpgAdapter(targetCpg: CpgStruct.Builder) extends CpgAdapter[Node.Builder, Node] {

  override def createNodeBuilder(kind: NodeKind): Node.Builder = {
    Node.newBuilder().setType(translateNodeKind(kind)).setKey(IdPool.getNextId)
  }

  override def createNode(nodeBuilder: Node.Builder): Node = {
    val node = nodeBuilder.build

    targetCpg.addNode(nodeBuilder)

    node
  }

  override def addProperty(nodeBuilder: Node.Builder, property: NodeProperty, value: String): Unit = {
    nodeBuilder.addStringProperty(translateNodeProperty(property), value)
  }

  override def addProperty(nodeBuilder: Node.Builder, property: NodeProperty, value: Int): Unit = {
    nodeBuilder.addIntProperty(translateNodeProperty(property), value)
  }

  override def addProperty(nodeBuilder: Node.Builder, property: NodeProperty, value: Boolean): Unit = {
    nodeBuilder.addBooleanProperty(translateNodeProperty(property), value)
  }

  override def addEdge(edgeKind: EdgeKind, dstNode: Node, srcNode: Node): Unit = {
    targetCpg.addEdge(newEdge(translateEdgeKind(edgeKind), dstNode, srcNode))
  }

  private def translateNodeProperty(nodeProperty: NodeProperty): NodePropertyName = {
    nodeProperty match {
      case NodeProperty.ORDER          => NodePropertyName.ORDER
      case NodeProperty.ARGUMENT_INDEX => NodePropertyName.ARGUMENT_INDEX
      case NodeProperty.NAME           => NodePropertyName.NAME
      case NodeProperty.FULL_NAME      => NodePropertyName.FULL_NAME
      case NodeProperty.CODE           => NodePropertyName.CODE
      case NodeProperty.EVALUATION_STRATEGY =>
        NodePropertyName.EVALUATION_STRATEGY
      case NodeProperty.TYPE_FULL_NAME => NodePropertyName.TYPE_FULL_NAME
      case NodeProperty.TYPE_DECL_FULL_NAME =>
        NodePropertyName.TYPE_DECL_FULL_NAME
      case NodeProperty.SIGNATURE        => NodePropertyName.SIGNATURE
      case NodeProperty.DISPATCH_TYPE    => NodePropertyName.DISPATCH_TYPE
      case NodeProperty.METHOD_FULL_NAME => NodePropertyName.METHOD_FULL_NAME
      case NodeProperty.METHOD_INST_FULL_NAME =>
        NodePropertyName.METHOD_INST_FULL_NAME
      case NodeProperty.IS_EXTERNAL      => NodePropertyName.IS_EXTERNAL
      case NodeProperty.PARSER_TYPE_NAME => NodePropertyName.PARSER_TYPE_NAME
      case NodeProperty.AST_PARENT_TYPE  => NodePropertyName.AST_PARENT_TYPE
      case NodeProperty.AST_PARENT_FULL_NAME =>
        NodePropertyName.AST_PARENT_FULL_NAME
      case NodeProperty.LINE_NUMBER          => NodePropertyName.LINE_NUMBER
      case NodeProperty.COLUMN_NUMBER        => NodePropertyName.COLUMN_NUMBER
      case NodeProperty.ALIAS_TYPE_FULL_NAME => NodePropertyName.ALIAS_TYPE_FULL_NAME
    }
  }

  private def translateNodeKind(nodeKind: NodeKind): Node.NodeType = {
    nodeKind match {
      case NodeKind.METHOD              => Node.NodeType.METHOD
      case NodeKind.METHOD_RETURN       => Node.NodeType.METHOD_RETURN
      case NodeKind.METHOD_PARAMETER_IN => Node.NodeType.METHOD_PARAMETER_IN
      case NodeKind.METHOD_INST         => Node.NodeType.METHOD_INST
      case NodeKind.CALL                => Node.NodeType.CALL
      case NodeKind.LITERAL             => Node.NodeType.LITERAL
      case NodeKind.IDENTIFIER          => Node.NodeType.IDENTIFIER
      case NodeKind.BLOCK               => Node.NodeType.BLOCK
      case NodeKind.RETURN              => Node.NodeType.RETURN
      case NodeKind.LOCAL               => Node.NodeType.LOCAL
      case NodeKind.TYPE                => Node.NodeType.TYPE
      case NodeKind.TYPE_DECL           => Node.NodeType.TYPE_DECL
      case NodeKind.MEMBER              => Node.NodeType.MEMBER
      case NodeKind.NAMESPACE_BLOCK     => Node.NodeType.NAMESPACE_BLOCK
      case NodeKind.UNKNOWN             => Node.NodeType.UNKNOWN
    }
  }

  private def translateEdgeKind(edgeKind: EdgeKind): Edge.EdgeType = {
    edgeKind match {
      case EdgeKind.AST => Edge.EdgeType.AST
      case EdgeKind.REF => Edge.EdgeType.REF
    }
  }
}
