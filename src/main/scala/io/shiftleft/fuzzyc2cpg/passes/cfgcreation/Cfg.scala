package io.shiftleft.fuzzyc2cpg.passes.cfgcreation

import io.shiftleft.codepropertygraph.generated.{EdgeTypes, nodes}
import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.Cfg.CfgEdgeType
import io.shiftleft.passes.DiffGraph
import org.slf4j.LoggerFactory

/**
  * A control flow graph that is under construction, consisting of:
  *
  * @param entryNode  the control flow graph's first node, that is,
  *                   the node to which a CFG that appends this CFG
  *                   should attach itself to.
  * @param diffGraphs control flow edges between nodes of the
  *                   code property graph.
  * @param fringe nodes of the CFG for which an outgoing edge type
  *               is already known but the destination node is not.
  *               These nodes are connected when another CFG is
  *               appended to this CFG.
  *
  * In addition to these three core building blocks, we store labels
  * and jump statements that have not been resolved and may be
  * resolvable as parent sub trees or sibblings are translated.
  *
  * @param labeledNodes labels contained in the abstract syntax tree
  *                     from which this CPG was generated
  * @param caseLabels labels beginning with "case"
  * @param breaks unresolved breaks collected along the way
  * @param continues unresolved continues collected along the way
  * @param gotos unresolved gotos collected along the way
  *
  * */
case class Cfg(entryNode: Option[nodes.CfgNode] = None,
               diffGraphs: List[DiffGraph.Builder] = List(),
               fringe: List[(nodes.CfgNode, CfgEdgeType)] = List(),
               labeledNodes: Map[String, nodes.CfgNode] = Map(),
               breaks: List[nodes.CfgNode] = List(),
               continues: List[nodes.CfgNode] = List(),
               caseLabels: List[nodes.CfgNode] = List(),
               gotos: List[(nodes.CfgNode, String)] = List()) {

  import Cfg._

  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * Create a new CFG in which `other` is appended
    * to this CFG. All nodes of the fringe are connected
    * to `other`'s entry node and the new fringe is
    * `other`'s fringe. The diffgraphs, jumps, and labels
    * are the sum of those present in `this` and `other`.
    *
    * */
  def ++(other: Cfg): Cfg = {
    if (other == Cfg.empty) {
      this
    } else if (this == Cfg.empty) {
      other
    } else {
      this.copy(
        fringe = other.fringe,
        diffGraphs = this.diffGraphs ++ other.diffGraphs ++
          edgesFromFringeTo(this, other.entryNode),
        gotos = this.gotos ++ other.gotos,
        labeledNodes = this.labeledNodes ++ other.labeledNodes,
        breaks = this.breaks ++ other.breaks,
        continues = this.continues ++ other.continues,
        caseLabels = this.caseLabels ++ other.caseLabels
      )
    }
  }

  def withFringeEdgeType(cfgEdgeType: CfgEdgeType): Cfg = {
    this.copy(fringe = fringe.map { case (x, _) => (x, cfgEdgeType) })
  }

  /**
    * Upon completing traversal of the abstract syntax tree,
    * this method creates CFG edges between gotos and
    * respective labels.
    * */
  def withResolvedGotos(): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    gotos.foreach {
      case (goto, label) =>
        labeledNodes.get(label) match {
          case Some(labeledNode) =>
            // TODO set edge type of Always once the backend
            // supports it
            diffGraph.addEdge(goto, labeledNode, EdgeTypes.CFG)
          case None =>
            logger.info("Unable to wire goto statement. Missing label {}.", label)
        }
    }
    this.copy(diffGraphs = this.diffGraphs ++ List(diffGraph))
  }

}

object Cfg {

  /**
    * The safe "null" Cfg.
    * */
  val empty: Cfg = new Cfg()

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

  /**
    * Create edges from all nodes of cfg's fringe to `node`.
    * */
  def edgesFromFringeTo(cfg: Cfg, node: Option[nodes.CfgNode]): List[DiffGraph.Builder] = {
    edgesFromFringeTo(cfg.fringe, node)
  }

  /**
    * Create edges from all nodes of cfg's fringe to `node`, ignoring fringe edge types
    * and using `cfgEdgeType` instead.
    * */
  def edgesFromFringeTo(cfg: Cfg, node: Option[nodes.CfgNode], cfgEdgeType: CfgEdgeType): List[DiffGraph.Builder] = {
    edges(cfg.fringe.map(_._1), node, cfgEdgeType)
  }

  /**
    * Create edges from a list (node, cfgEdgeType) pairs to `node`
    * */
  def edgesFromFringeTo(fringeElems: List[(nodes.CfgNode, CfgEdgeType)],
                        node: Option[nodes.CfgNode]): List[DiffGraph.Builder] = {
    // Note: the backend doesn't support Cfg edge types at the moment,
    // so we just ignore them for now.
    val diffGraph = DiffGraph.newBuilder
    fringeElems.foreach {
      case (sourceNode, cfgEdgeType) =>
        node.foreach { dstNode =>
          diffGraph.addEdge(sourceNode, dstNode, EdgeTypes.CFG)
        }
    }
    List(diffGraph)
  }

  /**
    * Create edges of given type from a list of source nodes to a destination node
    * */
  def edges(sources: List[nodes.CfgNode],
            dstNode: Option[nodes.CfgNode],
            cfgEdgeType: CfgEdgeType = AlwaysEdge): List[DiffGraph.Builder] = {
    edgesToMultiple(sources, dstNode.toList, cfgEdgeType)
  }

  def singleEdge(source: nodes.CfgNode,
                 destination: nodes.CfgNode,
                 cfgEdgeType: CfgEdgeType = AlwaysEdge): List[DiffGraph.Builder] = {
    edgesToMultiple(List(source), List(destination), cfgEdgeType)
  }

  /**
    * Create edges of given type from all nodes in `sources` to `node`.
    * */
  def edgesToMultiple(sources: List[nodes.CfgNode],
                      destinations: List[nodes.CfgNode],
                      cfgEdgeType: CfgEdgeType = AlwaysEdge): List[DiffGraph.Builder] = {
    // Note: the backend doesn't support Cfg edge types at the moment,
    // so we just ignore them for now.
    val diffGraph = DiffGraph.newBuilder
    sources.foreach { l =>
      destinations.foreach { n =>
        // TODO set `cfgEdgeType` once backend supports it
        diffGraph.addEdge(l, n, EdgeTypes.CFG)
      }
    }
    List(diffGraph)
  }

}
