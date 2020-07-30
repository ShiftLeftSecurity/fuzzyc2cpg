package io.shiftleft.fuzzyc2cpg.passes.cfgcreation

import io.shiftleft.codepropertygraph.generated.{Operators, nodes}
import io.shiftleft.codepropertygraph.generated.nodes.CfgNode
import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.Cfg.CfgEdgeType
import io.shiftleft.passes.DiffGraph
import io.shiftleft.semanticcpg.language._

/**
  * Translation of abstract syntax trees into control flow graphs
  *
  * The problem of translating an abstract syntax tree into a corresponding
  * control flow graph can be formulated as recursive problem in which
  * sub trees of the syntax tree are translated and their corresponding
  * control flow graphs and are connected according to the control flow
  * semantics of the root node.
  * For example, consider the abstract syntax tree for an if-statement:
  *
  *               (  if )
  *              /       \
  *          (x < 10)  (x += 1)
  *            / \       / \
  *           x  10     x   1
  *
  * This tree can be translated into a control flow graph, by translating
  * the sub tree rooted in `x < 10` and that of `x += 1` and connecting
  * their control flow graphs according to the semantics of `if`:
  *
  *            [x < 10]----
  *               |t     f|
  *            [x +=1 ]   |
  *               |
  * The semantics of if dictate that the first sub tree to the left
  * is a condition, which is connected to the CFG of the second sub
  * tree - the body of the if statement - via a control flow edge with
  * the `true` label (indicated in the illustration by `t`), and to the CFG
  * of any follow-up code via a `false` edge (indicated by `f`).
  *
  * A problem that becomes immediately apparent in the illustration is that
  * the result of translating a sub tree may leave us with edges for which
  * a source node is known but the destination not depends on parent or
  * siblings that were not considered in the translation. For example, we know
  * that an outgoing edge from [x<10] must exist, but we do not yet know where
  * it should lead. We refer to the set of nodes of the control flow graph with
  * outgoing edges for which the destination node is yet to be determined as
  * the "fringe" of the control flow graph.
  */
class CfgCreator(entryNode: nodes.Method) {

  import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.Cfg._
  import CfgCreator._

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

  /**
    * Conversion of a method to a CFG, showing the decomposition
    * of the control flow graph generation problem into that of
    * translating sub trees according to the node type. In the
    * particular case of a method, the CFG is obtained by
    * creating a CFG containing the single method node and
    * a fringe containing the node and an outgoing AlwaysEdge,
    * to the CFG obtained by translating child CFGs one by
    * one and appending them.
    * */
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

  /**
    * A second layer of dispatching for control structures. This could
    * as well be part of `cfgFor` and has only been placed into a
    * separate function to increase readability.
    * */
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

  /**
    * A CFG for a goto statement is one containing the goto
    * node as an entry node and an empty fringe. Moreover, we
    * store the goto for dispatching with `withResolvedGotos`
    * once the CFG for the entire method has been calculated.
    * */
  private def cfgForGotoStatement(node: nodes.ControlStructure): Cfg = {
    // TODO: the goto node should contain a field for the target so that
    // we can avoid the brittle split/slice operation here
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.map(t => Cfg(Some(node), gotos = List((node, t)))).getOrElse(Cfg.empty)
  }

  /**
    * Return statements may contain expressions as return values,
    * and therefore, the CFG for a return statement consists of
    * the CFG for calculation of that expression, appended to
    * a CFG containing only the return node, connected with
    * a single edge to the method exit node. The fringe is
    * empty.
    * */
  private def cfgForReturn(actualRet: nodes.Return): Cfg = {
    cfgForChildren(actualRet) ++
      Cfg(Some(actualRet), singleEdge(actualRet, exitNode), List())
  }

  /**
    * The right hand side of a logical AND expression is only evaluated
    * if the left hand side is true as the entire expression can only
    * be true if both expressions are true. This is encoded in the
    * corresponding control flow graph by creating control flow graphs
    * for the left and right hand expressions and appending the two,
    * where the fringe edge type of the left CFG is `TrueEdge`.
    * */
  def cfgForAndExpression(call: nodes.Call): Cfg = {
    val leftCfg = cfgFor(call.argument(1))
    val rightCfg = cfgFor(call.argument(2))
    val diffGraphs = edgesFromFringeTo(leftCfg, rightCfg.entryNode, TrueEdge) ++ leftCfg.diffGraphs ++ rightCfg.diffGraphs
    Cfg(leftCfg.entryNode, diffGraphs, leftCfg.fringe ++ rightCfg.fringe) ++ cfgForSingleNode(call)
  }

  /**
    * Same construction recipe as for the AND expression, just that the fringe edge type
    * of the left CFG is `FalseEdge`.
    * */
  def cfgForOrExpression(call: nodes.Call): Cfg = {
    val leftCfg = cfgFor(call.argument(1))
    val rightCfg = cfgFor(call.argument(2))
    val diffGraphs = edgesFromFringeTo(leftCfg, rightCfg.entryNode, FalseEdge) ++ leftCfg.diffGraphs ++ rightCfg.diffGraphs
    Cfg(leftCfg.entryNode, diffGraphs, leftCfg.fringe ++ rightCfg.fringe) ++ cfgForSingleNode(call)
  }

  /**
    * A conditional expression is of the form `condition ? trueExpr ; falseExpr`
    * We create the corresponding CFGs by creating CFGs for the three expressions
    * and adding edges between them. The new entry node is the condition entry
    * node.
    * */
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

  /**
    * A for statement is of the form `for(initExpr; condition; loopExpr) body`
    * and all four components may be empty. The sequence
    * (condition - body - loopExpr) form the inner part of the loop
    * and we calculate the corresponding CFG `innerCfg` so that it is no longer
    * relevant which of these three actually exist and we still have an entry
    * node for the loop and a fringe.
    * */
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
          edgesFromFringeTo(conditionCfg, bodyCfg.entryNode, TrueEdge)
        } else {
          edgesFromFringeTo(conditionCfg, conditionCfg.entryNode, TrueEdge)
        }
      }
    Cfg(
      if (bodyCfg != Cfg.empty) { bodyCfg.entryNode } else { conditionCfg.entryNode },
      diffGraphs ++ bodyCfg.diffGraphs ++ conditionCfg.diffGraphs,
      conditionCfg.fringe.withEdgeType(FalseEdge) ++ bodyCfg.breaks.map((_, AlwaysEdge))
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

    val diffGraphs = edgesFromFringeTo(conditionCfg, trueCfg.entryNode) ++
      edgesFromFringeTo(conditionCfg, falseCfg.entryNode)

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

object CfgCreator {

  implicit class FringeWrapper(fringe: List[(nodes.CfgNode, CfgEdgeType)]) {
    def withEdgeType(edgeType: CfgEdgeType): List[(CfgNode, CfgEdgeType)] = {
      fringe.map { case (x, _) => (x, edgeType) }
    }
  }

}
