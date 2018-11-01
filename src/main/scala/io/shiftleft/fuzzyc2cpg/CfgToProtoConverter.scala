package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.cfg.CFG
import io.shiftleft.fuzzyc2cpg.cfg.nodes.{ASTNodeContainer, CfgEntryNode, CfgExitNode}
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object CfgToProtoConverter {
  private val logger = LoggerFactory.getLogger(getClass)
}

/**
  * Convert from an overlay CFG graph to target inlined CFG in proto format.
  * The soure overlay CFG is pointing to the AST nodes coming from the parser.
  * @param cfg To be converted source CFG.
  * @param methodEntry The CFG entry of the target proto format.
  * @param methodExit The CFG exit of the target proto format.
  * @param astToProtoMapping Mapping between source AST and already converted proto nodes.
  * @param targetCpg Target CPG container to which we add the CFG.
  */
class CfgToProtoConverter(cfg: CFG,
                          methodEntry: Node,
                          methodExit: Node,
                          astToProtoMapping: Map[AstNode, Node],
                          targetCpg: CpgStruct.Builder) {
  import CfgToProtoConverter._

  def convert(): Unit = {
    for (cfgNode <- cfg.asScala) {
      cfgNode match {
        case cfgEntryNode: CfgEntryNode =>
          convertEntries(cfgEntryNode)
        case cfgNode: ASTNodeContainer =>
          convertNode(cfgNode)
        case cfgExitNode: CfgExitNode =>

      }
    }
  }

  private def convertEntries(cfgEntryNode: CfgEntryNode): Unit = {
    for (cfgEdge <- cfg.outgoingEdges(cfgEntryNode).asScala) {
      cfgEdge.getDestination match {
        case cfgDstNode: ASTNodeContainer =>
          astToProtoMapping.get(cfgDstNode.getASTNode) match {
            case Some(protoDstNode) =>
              targetCpg.addEdge(newEdge(EdgeType.CFG, protoDstNode, methodEntry))
            case None =>
              logger.warn("Unable to translate CFG edge. Target CFG will be incomplete.")
          }
        case _: CfgExitNode =>
          // For otherwise empty CFG we do not draw an edge from entry to exit.
      }
    }
  }

  private def convertNode(cfgSrcNode: ASTNodeContainer): Unit = {
    astToProtoMapping.get(cfgSrcNode.getASTNode) match {
      case Some(protoSrcNode) =>
        for (cfgEdge <- cfg.outgoingEdges(cfgSrcNode).asScala) {
          cfgEdge.getDestination match {
            case cfgDstNode: ASTNodeContainer =>
              astToProtoMapping.get(cfgDstNode.getASTNode) match {
                case Some(protoDstNode) =>
                  targetCpg.addEdge(newEdge(EdgeType.CFG, protoDstNode, protoSrcNode))
                case None =>
                  logger.warn("Unable to translate CFG edge. Target CFG will be incomplete.")
              }
            case _: CfgExitNode =>
              targetCpg.addEdge(newEdge(EdgeType.CFG, methodExit, protoSrcNode))
          }
        }
      case None =>
        logger.warn("Unable to translate CFG edge. Target CFG will be incomplete.")
    }
  }

}
