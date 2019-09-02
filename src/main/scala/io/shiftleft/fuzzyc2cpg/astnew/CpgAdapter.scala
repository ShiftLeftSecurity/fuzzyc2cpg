package io.shiftleft.fuzzyc2cpg.astnew

import io.shiftleft.fuzzyc2cpg.astnew.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeProperty.NodeProperty

object NodeProperty extends Enumeration {
  type NodeProperty = Value
  val ORDER, ARGUMENT_INDEX, NAME, FULL_NAME, CODE, EVALUATION_STRATEGY, TYPE_FULL_NAME, TYPE_DECL_FULL_NAME, SIGNATURE,
  DISPATCH_TYPE, METHOD_FULL_NAME, METHOD_INST_FULL_NAME, IS_EXTERNAL, PARSER_TYPE_NAME, AST_PARENT_TYPE,
  AST_PARENT_FULL_NAME, LINE_NUMBER, COLUMN_NUMBER, ALIAS_TYPE_FULL_NAME = Value
}

object NodeKind extends Enumeration {
  type NodeKind = Value
  val METHOD, METHOD_RETURN, METHOD_PARAMETER_IN, METHOD_INST, CALL, LITERAL, IDENTIFIER, BLOCK, RETURN, LOCAL, TYPE,
  TYPE_DECL, MEMBER, NAMESPACE_BLOCK, CONTROL_STRUCTURE, UNKNOWN = Value
}

object EdgeKind extends Enumeration {
  type EdgeKind = Value
  val AST, REF, CONDITION = Value
}

trait CpgAdapter[NodeBuilderType, NodeType] {
  def createNodeBuilder(kind: NodeKind): NodeBuilderType

  def createNode(nodeBuilder: NodeBuilderType): NodeType

  def addProperty(nodeBuilder: NodeBuilderType, property: NodeProperty, value: String): Unit

  def addProperty(nodeBuilder: NodeBuilderType, property: NodeProperty, value: Int): Unit

  def addProperty(nodeBuilder: NodeBuilderType, property: NodeProperty, value: Boolean): Unit

  def addEdge(edgeKind: EdgeKind, dstNode: NodeType, srcNode: NodeType): Unit
}
