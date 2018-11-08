package io.shiftleft.fuzzyc2cpg.cfgnew

import io.shiftleft.fuzzyc2cpg.ast.AstNode

trait CfgEdgeType
object TrueEdge extends CfgEdgeType
object FalseEdge extends CfgEdgeType
object AlwaysEdge extends CfgEdgeType

trait DestinationGraphAdapter[NodeType] {
  def mapNode(astNode: AstNode): NodeType
  def newCfgEdge(dstNode: NodeType, srcNode: NodeType, cfgEdgeType: CfgEdgeType)
}
