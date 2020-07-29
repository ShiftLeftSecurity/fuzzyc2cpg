package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.passes.CfgCreatorForMethod.{AlwaysEdge, CfgEdgeType}
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

/**
  * A pass that creates control flow graphs from abstract syntax trees.
  *
  * Control flow graphs can be calculated independently per method.
  * Therefore, we inherit from `ParallelCpgPass`. As for other
  * parallel passes, we provide a key pool that is split into equal
  * parts, each of which is assigned to exactly one method prior
  * to branching off into parallel computation. This ensures id
  * stability over multiple runs.
  *
  * Note: the version of OverflowDB that we currently use as a
  * storage backend does not assign ids to edges and this pass
  * only creates edges at the moment. Therefore, we could do
  * without key pools, however, this would not only deviate
  * from the standard template for parallel CPG passes but it
  * is also likely to bite us later, whenever we find that
  * adding nodes in this pass or adding edge ids to the
  * backend becomes necessary.
  * */
class CfgCreationPass(cpg: Cpg, keyPool: IntervalKeyPool)
    extends ParallelCpgPass[nodes.Method](cpg, keyPools = Some(keyPool.split(cpg.method.size))) {

  override def partIterator: Iterator[nodes.Method] = cpg.method.iterator

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] =
    new CfgCreatorForMethod(method).run()

}

object Cfg {

  /**
    * The safe "null" Cfg.
    * */
  val empty: Cfg = new Cfg()

  /**
    * Create edges from all nodes of cfg's fringe to `node`.
    * */
  def edgesFromFringeTo(cfg: Cfg,
                        node: Option[nodes.CfgNode],
                        cfgEdgeType: CfgEdgeType = AlwaysEdge): List[DiffGraph.Builder] =
    edges(cfg.fringe.map(_._1), node, cfgEdgeType)

  def edges(sources: List[nodes.CfgNode],
            dstNode: Option[nodes.CfgNode],
            cfgEdgeType: CfgEdgeType = AlwaysEdge): List[DiffGraph.Builder] = {
    edgesToMultiple(sources, dstNode.toList, cfgEdgeType)
  }

  /**
    * Create edges from all in `sources` to `node`.
    * */
  def edgesToMultiple(sources: List[nodes.CfgNode],
                      destinations: List[nodes.CfgNode],
                      cfgEdgeType: CfgEdgeType = AlwaysEdge): List[DiffGraph.Builder] = {
    // Note: the backend doesn't support Cfg edge types at the moment,
    // so we just ignore them for now.
    val diffGraph = DiffGraph.newBuilder
    sources.foreach { l =>
      destinations.foreach { n =>
        diffGraph.addEdge(l, n, EdgeTypes.CFG)
      }
    }
    List(diffGraph)
  }

}

/**
  * A control flow graph that is under construction, consisting of:
  *
  * @param entryNode  the control flow graph's first node, that is,
  *                   the node to which a CFG that appends this CFG
  *                   should attach itself to.
  * @param diffGraphs control flow edges between nodes of the
  *                   code property graph.
  * @param fringe think of this as the control flow graph's last nodes,
  *               that is, the nodes that should be connected to any
  *               CFG that we append.
  *
  * @param labeledNodes labels contained in the abstract syntax tree
  *                     from which this CPG was generated
  * @param caseLabels labels beginning with "case"
  *
  * @param breaks unresolved breaks collected along the way
  * @param continues unresolved continues collected along the way
  * @param gotos unresolved gotos collected along the way
  *
  * In principle, sub trees of the abstract syntax tree
  * can be translated to CFGs and these can be combined.
  * However, a syntax tree may contain unstructured control
  * flow elements such as `continues`, `breaks` or `gotos`
  * that cannot be translated until parent sub trees are
  * processed. During construction of the CFG, we therefore
  * need a data structure that contains the CFG along with
  * any jumps and labels that have not yet been translated,
  * and `CFG` provides this data structure.
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
            diffGraph.addEdge(goto, labeledNode, EdgeTypes.CFG)
          case None =>
            logger.info("Unable to wire goto statement. Missing label {}.", label)
        }
    }
    this.copy(diffGraphs = this.diffGraphs ++ List(diffGraph))
  }

}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  import Cfg._
  import CfgCreatorForMethod._

  /**
    * Control flow graph definitions often feature a designated entry
    * and exit node for each method. While these nodes are no-ops
    * from a computational point of view, they are useful to
    * guarantee that a method has exactly one entry and one exit.
    *
    * For the CPG-based control flow graph, we do not need to
    * introduce fake entry and exit node. Instead, we can use the
    * METHOD and METHOD_RETURN nodes as entry and exit nodes
    * respectively. Note that METHOD_RETURN nodes are the nodes
    * representing formal return parameters, of which there exists
    * exactly one per method.
    * */
  private val exitNode: nodes.MethodReturn = entryNode.methodReturn

  /**
    * We return the CFG as a sequence of Diff Graphs that is
    * calculated by first obtaining the CFG for the method
    * and then resolving gotos.
    * */
  def run(): Iterator[DiffGraph] =
    cfgForMethod(entryNode)
      .withResolvedGotos()
      .diffGraphs
      .map(_.build)
      .iterator

  private def cfgForMethod(node: nodes.Method): Cfg =
    cfgForSingleNode(node) ++ cfgForChildren(node)

  /**
    * For any single AST node, we can construct a CFG
    * containing that single node by setting it as
    * the entry node and placing it in the fringe.
    * */
  private def cfgForSingleNode(node: nodes.CfgNode): Cfg =
    Cfg(Some(node), fringe = List((node, AlwaysEdge)))

  /**
    * The CFG for all children is obtained by translating
    * child ASTs one by one from left to right and appending
    * them.
    * */
  private def cfgForChildren(node: nodes.AstNode): Cfg =
    node.astChildren.l.map(cfgFor).reduceOption((x, y) => x ++ y).getOrElse(Cfg.empty)

  /**
    * This method dispatches AST nodes by type and calls
    * corresponding conversion methods.
    * */
  private def cfgFor(node: nodes.AstNode): Cfg =
    node match {
      case n: nodes.ControlStructure =>
        cfgForControlStructure(n)
      case n: nodes.JumpTarget =>
        cfgForJumpTarget(n)
      case actualRet: nodes.Return => cfgForReturn(actualRet)
      case call: nodes.Call if call.name == Operators.logicalAnd =>
        cfgForAndExpression(call)
      case call: nodes.Call if call.name == Operators.logicalOr =>
        cfgForOrExpression(call)
      case call: nodes.Call if call.name == Operators.conditional =>
        cfgForConditionalExpression(call)
      case (_: nodes.Call | _: nodes.Identifier | _: nodes.Literal | _: nodes.MethodReturn) =>
        cfgForChildren(node) ++ cfgForSingleNode(node.asInstanceOf[nodes.CfgNode])
      case _ =>
        cfgForChildren(node)
    }

  private def cfgForControlStructure(node: nodes.ControlStructure): Cfg =
    node.parserTypeName match {
      case "BreakStatement" =>
        cfgForBreakStatement(node)
      case "ContinueStatement" =>
        cfgForContinueStatement(node)
      case "WhileStatement" =>
        cfgForWhileStatement(node)
      case "DoStatement" =>
        cfgForDoStatement(node)
      case "ForStatement" =>
        cfgForForStatement(node)
      case "GotoStatement" =>
        cfgForGotoStatement(node)
      case "IfStatement" =>
        cfgForIfStatement(node)
      case "ElseStatement" =>
        cfgForChildren(node)
      case "SwitchStatement" =>
        cfgForSwitchStatement(node)
      case _ =>
        Cfg.empty
    }

  /**
    * The CFG for a break/continue statements contains only
    * the break/continue statement as a single entry node.
    * The fringe is empty, that is, appending
    * another CFG to the break statement will
    * not result in the creation of an edge from
    * the break statement to the entry point
    * of the other CFG.
    * */
  private def cfgForBreakStatement(node: nodes.ControlStructure): Cfg =
    Cfg(Some(node), breaks = List(node))

  private def cfgForContinueStatement(node: nodes.ControlStructure): Cfg =
    Cfg(Some(node), continues = List(node))

  /**
    * Jump targets ("labels") are included in the CFG. As these
    * should be connected to the next appended CFG, we specify
    * that the label node is both the entry node and the only
    * node in the fringe. This is achieved by calling `cfgForSingleNode`
    * on the label node. Just like for breaks and continues, we record
    * labels. We store case/default labels separately from other labels,
    * but that is not a relevant implementation detail.
    * */
  private def cfgForJumpTarget(n: nodes.JumpTarget): Cfg = {
    val labelName = n.name
    val cfg = cfgForSingleNode(n)
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      cfg.copy(caseLabels = List(n))
    } else {
      cfg.copy(labeledNodes = Map(labelName -> n))
    }
  }

  private def cfgForGotoStatement(node: nodes.ControlStructure): Cfg = {
    // TODO: the goto node should contain a field for the target so that
    // we can avoid the brittle split/slice operation here
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.map(t => Cfg(Some(node), gotos = List((node, t)))).getOrElse(Cfg.empty)
  }

  private def cfgForReturn(actualRet: nodes.Return): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    diffGraph.addEdge(actualRet, exitNode, EdgeTypes.CFG)
    cfgForChildren(actualRet) ++ Cfg(Some(actualRet), List(diffGraph), fringe = List())
  }

  def cfgForAndExpression(call: nodes.Call): Cfg = {
    val leftCfg = cfgFor(call.argument(1))
    val rightCfg = cfgFor(call.argument(2))
    val diffGraphs = edgesFromFringeTo(leftCfg, rightCfg.entryNode, TrueEdge) ++ leftCfg.diffGraphs ++ rightCfg.diffGraphs
    Cfg(leftCfg.entryNode, diffGraphs, leftCfg.fringe ++ rightCfg.fringe) ++ cfgForSingleNode(call)
  }

  def cfgForOrExpression(call: nodes.Call): Cfg = {
    val leftCfg = cfgFor(call.argument(1))
    val rightCfg = cfgFor(call.argument(2))
    val diffGraphs = edgesFromFringeTo(leftCfg, rightCfg.entryNode, FalseEdge) ++ leftCfg.diffGraphs ++ rightCfg.diffGraphs
    Cfg(leftCfg.entryNode, diffGraphs, leftCfg.fringe ++ rightCfg.fringe) ++ cfgForSingleNode(call)
  }

  private def cfgForConditionalExpression(call: nodes.Call): Cfg = {
    val conditionCfg = cfgFor(call.argument(1))
    val trueCfg = cfgFor(call.argument(2))
    val falseCfg = cfgFor(call.argument(3))
    val diffGraphs = edgesFromFringeTo(conditionCfg, trueCfg.entryNode, TrueEdge) ++
      edgesFromFringeTo(conditionCfg, falseCfg.entryNode, FalseEdge)

    Cfg(conditionCfg.entryNode,
        conditionCfg.diffGraphs ++ trueCfg.diffGraphs ++ falseCfg.diffGraphs ++ diffGraphs,
        trueCfg.fringe ++ falseCfg.fringe) ++ cfgForSingleNode(call)
  }

  private def cfgForForStatement(node: nodes.ControlStructure): Cfg = {
    val children = node.astChildren.l
    val initExprCfg = children.find(_.order == 1).map(cfgFor).getOrElse(Cfg.empty)
    val conditionCfg = children.find(_.order == 2).map(cfgFor).getOrElse(Cfg.empty)
    val loopExprCfg = children.find(_.order == 3).map(cfgFor).getOrElse(Cfg.empty)
    val bodyCfg = children.find(_.order == 4).map(cfgFor).getOrElse(Cfg.empty)

    val innerCfg = conditionCfg ++ bodyCfg ++ loopExprCfg
    val entryNode = (initExprCfg ++ innerCfg).entryNode

    val diffGraphs = edgesFromFringeTo(initExprCfg, innerCfg.entryNode) ++
      edgesFromFringeTo(innerCfg, innerCfg.entryNode) ++
      edgesFromFringeTo(conditionCfg, bodyCfg.entryNode, TrueEdge) ++ {
      if (loopExprCfg.entryNode.isDefined) {
        edges(bodyCfg.continues, loopExprCfg.entryNode)
      } else {
        edges(bodyCfg.continues, innerCfg.entryNode)
      }
    }

    Cfg(entryNode,
        diffGraphs ++ initExprCfg.diffGraphs ++ innerCfg.diffGraphs,
        conditionCfg.fringe.withEdgeType(FalseEdge) ++ bodyCfg.breaks.map((_, AlwaysEdge)))
  }

  private def cfgForDoStatement(node: nodes.ControlStructure): Cfg = {
    val bodyCfg = node.astChildren.filter(_.order(1)).headOption.map(cfgFor).getOrElse(Cfg.empty)
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)

    val diffGraphs =
      edges(bodyCfg.continues, conditionCfg.entryNode) ++
        edgesFromFringeTo(bodyCfg, conditionCfg.entryNode) ++ {
        if (bodyCfg.entryNode.isDefined) {
          edgesFromFringeTo(conditionCfg, bodyCfg.entryNode)
        } else {
          edgesFromFringeTo(conditionCfg, conditionCfg.entryNode)
        } ++ {
          if (bodyCfg.entryNode.isDefined) {
            edgesFromFringeTo(conditionCfg, bodyCfg.entryNode)
          } else {
            edgesFromFringeTo(conditionCfg, conditionCfg.entryNode)
          }
        }
      }
    Cfg(
      if (bodyCfg != Cfg.empty) { bodyCfg.entryNode } else { conditionCfg.entryNode },
      diffGraphs ++ bodyCfg.diffGraphs ++ conditionCfg.diffGraphs,
      conditionCfg.fringe ++ bodyCfg.breaks.map((_, AlwaysEdge))
    )
  }

  private def cfgForWhileStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val trueCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val diffGraphs = edgesFromFringeTo(conditionCfg, trueCfg.entryNode) ++
      edgesFromFringeTo(trueCfg, conditionCfg.entryNode) ++
      edges(trueCfg.continues, conditionCfg.entryNode)

    Cfg(
      conditionCfg.entryNode,
      diffGraphs ++ conditionCfg.diffGraphs ++ trueCfg.diffGraphs,
      conditionCfg.fringe.withEdgeType(FalseEdge) ++ trueCfg.breaks.map((_, AlwaysEdge))
    )
  }

  private def cfgForSwitchStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val bodyCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val diffGraphs = edgesToMultiple(conditionCfg.fringe.map(_._1), bodyCfg.caseLabels)

    val hasDefaultCase = bodyCfg.caseLabels.exists(x => x.asInstanceOf[nodes.JumpTarget].name == "default")

    Cfg(
      conditionCfg.entryNode,
      diffGraphs ++ conditionCfg.diffGraphs ++ bodyCfg.diffGraphs,
      { if (!hasDefaultCase) { conditionCfg.fringe.withEdgeType(FalseEdge) } else { List() } } ++ bodyCfg.breaks
        .map((_, AlwaysEdge)) ++ bodyCfg.fringe
    )
  }

  private def cfgForIfStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val trueCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val falseCfg = node.start.whenFalse.headOption.map(cfgFor).getOrElse(Cfg.empty)

    val diffGraphs = edgesFromFringeTo(conditionCfg, trueCfg.entryNode, TrueEdge) ++
      edgesFromFringeTo(conditionCfg, falseCfg.entryNode, FalseEdge)

    val diffGraph = DiffGraph.newBuilder
    Cfg(
      conditionCfg.entryNode,
      diffGraphs ++ List(diffGraph) ++ conditionCfg.diffGraphs ++ trueCfg.diffGraphs ++ falseCfg.diffGraphs,
      trueCfg.fringe ++ {
        if (falseCfg.entryNode.isDefined) {
          falseCfg.fringe
        } else {
          conditionCfg.fringe.withEdgeType(FalseEdge)
        }
      }
    )
  }

}

object CfgCreatorForMethod {

  implicit class FringeWrapper(fringe: List[(nodes.CfgNode, CfgEdgeType)]) {

    def withEdgeType(edgeType: CfgEdgeType): List[(CfgNode, CfgEdgeType)] = {
      fringe.map { case (x, _) => (x, edgeType) }
    }

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

}
