package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.proto.cpg.Cpg
import io.shiftleft.proto.cpg.Cpg.CpgStruct.{Edge, Node}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Property
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, PropertyValue}

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

  def newEdge(edgeType: Edge.EdgeType, dstNode: Node, srcNode: Node): Edge.Builder = {
    Edge.newBuilder()
      .setType(edgeType)
      .setDst(dstNode.getKey)
      .setSrc(srcNode.getKey)
  }

  def children(node: AstNode) =
    (0 to node.getChildCount).map(node.getChild)
      .filterNot(_ == null)
      .toList

  implicit class NodeBuilderWrapper(nodeBuilder: Node.Builder) {
    def addStringProperty(name: Cpg.NodePropertyName, value: String): Node.Builder = {
      nodeBuilder.addProperty(newStringProperty(name, value))
    }
    def addIntProperty(name: Cpg.NodePropertyName, value: Int): Node.Builder = {
      nodeBuilder.addProperty(newIntProperty(name, value))
    }
  }

  implicit class CpgStructBuilderWrapper(cpgStructBuilder: CpgStruct.Builder) {
    def addEdge(edgeType: Edge.EdgeType, dstNode: Node, srcNode: Node): CpgStruct.Builder = {
      cpgStructBuilder.addEdge(newEdge(edgeType, dstNode, srcNode))
    }
  }


}
