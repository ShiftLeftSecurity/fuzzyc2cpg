package io.shiftleft.fuzzyc2cpg.cfg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType

class ProtoGraphAdapter(targetCpg: CpgStruct.Builder,
                        astToProtoMapping: Map[AstNode, Node]) extends DestinationGraphAdapter[Node] {
  override def mapNode(astNode: AstNode): Node = {
    astToProtoMapping(astNode)
  }

  override def newCfgEdge(dstNode: Node, srcNode: Node, cfgEdgeType: CfgEdgeType): Unit = {
    targetCpg.addEdge(newEdge(EdgeType.CFG, dstNode, srcNode))
  }
}
