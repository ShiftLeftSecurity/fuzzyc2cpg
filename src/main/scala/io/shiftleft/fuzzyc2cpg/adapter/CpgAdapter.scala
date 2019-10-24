package io.shiftleft.fuzzyc2cpg.adapter

import io.shiftleft.fuzzyc2cpg.adapter.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.adapter.EdgeProperty.EdgeProperty
import io.shiftleft.fuzzyc2cpg.adapter.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.adapter.NodeProperty.NodeProperty
import io.shiftleft.fuzzyc2cpg.ast.AstNode

object NodeProperty extends Enumeration {
  type NodeProperty = Value
  val ORDER, ARGUMENT_INDEX, NAME, FULL_NAME, CODE, EVALUATION_STRATEGY, TYPE_FULL_NAME, TYPE_DECL_FULL_NAME, SIGNATURE,
  DISPATCH_TYPE, METHOD_FULL_NAME, METHOD_INST_FULL_NAME, IS_EXTERNAL, PARSER_TYPE_NAME, AST_PARENT_TYPE,
  AST_PARENT_FULL_NAME, LINE_NUMBER, COLUMN_NUMBER, LINE_NUMBER_END, COLUMN_NUMBER_END, ALIAS_TYPE_FULL_NAME = Value
}

object NodeKind extends Enumeration {
  type NodeKind = Value
  val METHOD, METHOD_RETURN, METHOD_PARAMETER_IN, METHOD_INST, CALL, LITERAL, IDENTIFIER, BLOCK, RETURN, LOCAL, TYPE,
  TYPE_DECL, MEMBER, NAMESPACE_BLOCK, CONTROL_STRUCTURE, UNKNOWN = Value
}

object EdgeProperty extends Enumeration {
  type EdgeProperty = Value
  val CFG_EDGE_TYPE = Value
}

object EdgeKind extends Enumeration {
  type EdgeKind = Value
  val AST, CFG, REF, CONDITION = Value
}

trait CfgEdgeType
object TrueEdge extends CfgEdgeType {
  override def toString: String = "TrueEdge"
}
object FalseEdge extends CfgEdgeType {
  override def toString: String = "FalseEdge"
}
object AlwaysEdge extends CfgEdgeType {
  override def toString: String = "AlwaysEdge"
}
object CaseEdge extends CfgEdgeType {
  override def toString: String = "CaseEdge"
}

trait CpgAdapter[NodeBuilderType, NodeType, EdgeBuilderType, EdgeType] {
  def createNodeBuilder(kind: NodeKind): NodeBuilderType

  def createNode(nodeBuilder: NodeBuilderType): NodeType

  def createNode(nodeBuilder: NodeBuilderType, origAstNode: AstNode): NodeType

  def addNodeProperty(nodeBuilder: NodeBuilderType, property: NodeProperty, value: String)

  def addNodeProperty(nodeBuilder: NodeBuilderType, property: NodeProperty, value: Int)

  def addNodeProperty(nodeBuilder: NodeBuilderType, property: NodeProperty, value: Boolean)

  def createEdgeBuilder(dst: NodeType, src: NodeType, edgeKind: EdgeKind): EdgeBuilderType

  def createEdge(edgeBuilder: EdgeBuilderType): EdgeType

  def addEdgeProperty(edgeBuilder: EdgeBuilderType, property: EdgeProperty, value: String)

  def mapNode(astNode: AstNode): NodeType
}
