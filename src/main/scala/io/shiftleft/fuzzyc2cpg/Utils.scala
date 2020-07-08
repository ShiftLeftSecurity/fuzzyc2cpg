package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.proto.cpg.Cpg
import io.shiftleft.proto.cpg.Cpg.CpgStruct.{Edge, Node}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, PropertyValue, StringList}

object Utils {

  def newStringProperty(name: Cpg.NodePropertyName, value: String): Property.Builder = {
    Property.newBuilder
      .setName(name)
      .setValue(PropertyValue.newBuilder.setStringValue(value).build)
  }

  def newIntProperty(name: Cpg.NodePropertyName, value: Int): Property.Builder = {
    Property.newBuilder
      .setName(name)
      .setValue(PropertyValue.newBuilder.setIntValue(value).build)
  }

  def newBooleanProperty(name: Cpg.NodePropertyName, value: Boolean): Property.Builder = {
    Property.newBuilder
      .setName(name)
      .setValue(PropertyValue.newBuilder.setBoolValue(value).build)
  }

  def newStringListProperty(name: Cpg.NodePropertyName, value: List[String]): Property.Builder = {
    val slb = StringList.newBuilder()
    value.map { slb.addValues(_) }
    slb.build()
    Property.newBuilder
      .setName(name)
      .setValue(PropertyValue.newBuilder.setStringList(slb).build)
  }

  def newNode(nodeType: NodeType): Node.Builder = {
    Node
      .newBuilder()
      .setType(nodeType)
  }

  def newEdge(edgeType: Edge.EdgeType, dstNode: Node, srcNode: Node): Edge.Builder = {
    Edge
      .newBuilder()
      .setType(edgeType)
      .setDst(dstNode.getKey)
      .setSrc(srcNode.getKey)
  }

  def children(node: AstNode) =
    (0 to node.getChildCount)
      .map(node.getChild)
      .filterNot(_ == null)
      .toList

  def getGlobalNamespaceBlockFullName(fileNameOption: Option[String]): String = {
    fileNameOption match {
      case Some(fileName) =>
        s"$fileName:${Defines.globalNamespaceName}"
      case None =>
        Defines.globalNamespaceName
    }
  }

  implicit class NodeBuilderWrapper(nodeBuilder: Node.Builder) {
    def addStringProperty(name: Cpg.NodePropertyName, value: String): Node.Builder = {
      nodeBuilder.addProperty(newStringProperty(name, value))
    }
    def addIntProperty(name: Cpg.NodePropertyName, value: Int): Node.Builder = {
      nodeBuilder.addProperty(newIntProperty(name, value))
    }
    def addBooleanProperty(name: Cpg.NodePropertyName, value: Boolean): Node.Builder = {
      nodeBuilder.addProperty(newBooleanProperty(name, value))
    }
    def addStringListProperty(name: Cpg.NodePropertyName, value: List[String]): Node.Builder = {
      nodeBuilder.addProperty(newStringListProperty(name, value))
    }

  }

  implicit class CpgStructBuilderWrapper(cpgStructBuilder: CpgStruct.Builder) {
    def addEdge(edgeType: Edge.EdgeType, dstNode: Node, srcNode: Node): CpgStruct.Builder = {
      cpgStructBuilder.addEdge(newEdge(edgeType, dstNode, srcNode))
    }
  }

}
