package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.passes.CfgCreatorForMethod.FringeElement
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

import scala.collection.mutable

class CfgCreationPass(cpg: Cpg, keyPool: IntervalKeyPool)
    extends ParallelCpgPass[nodes.Method](cpg, keyPools = Some(keyPool.split(cpg.method.size))) {

  override def partIterator: Iterator[nodes.Method] = cpg.method.iterator

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] =
    new CfgCreatorForMethod(method).run()

}

object Cfg {
  val empty: Cfg = Cfg()
}

case class Cfg(entryNode: Option[nodes.CfgNode] = None,
               diffGraphs: List[DiffGraph.Builder] = List(),
               fringe: List[FringeElement] = List(),
               labeledNodes: Map[String, nodes.CfgNode] = Map(),
               breaks: List[nodes.CfgNode] = List(),
               continues: List[nodes.CfgNode] = List(),
               caseLabels: List[nodes.CfgNode] = List(),
               gotos: List[(nodes.CfgNode, String)] = List()) {

  import CfgCreatorForMethod._

  private val logger = LoggerFactory.getLogger(getClass)

  def withResolvedGotos(): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    gotos.foreach {
      case (goto, label) =>
        labeledNodes.get(label) match {
          case Some(labeledNode) =>
            diffGraph.addEdge(
              goto,
              labeledNode,
              EdgeTypes.CFG
            )
          case None =>
            logger.info("Unable to wire goto statement. Missing label {}.", label)
        }
    }
    this.copy(diffGraphs = diffGraphs ++ List(diffGraph))
  }

  def ++(other: Cfg): Cfg = {
    if (other == Cfg.empty) {
      this
    } else if (this == Cfg.empty) {
      other
    } else {
      val diffGraph = DiffGraph.newBuilder
      fringe.foreach {
        case FringeElement(src, _) =>
          other.entryNode.foreach { entry =>
            diffGraph.addEdge(src, entry, EdgeTypes.CFG)
          }
      }
      this.copy(
        fringe = other.fringe,
        diffGraphs = diffGraphs ++ other.diffGraphs ++ mutable.ListBuffer(diffGraph),
        gotos = this.gotos ++ other.gotos,
        labeledNodes = this.labeledNodes ++ other.labeledNodes,
        breaks = this.breaks ++ other.breaks,
        continues = this.continues ++ other.continues,
        caseLabels = this.caseLabels ++ other.caseLabels
      )
    }
  }

}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  import CfgCreatorForMethod._

  private val exitNode: nodes.MethodReturn = entryNode.methodReturn

  def run(): Iterator[DiffGraph] =
    cfgForMethod(entryNode)
      .withResolvedGotos()
      .diffGraphs
      .map(_.build)
      .iterator

  private def cfgForMethod(node: nodes.Method): Cfg =
    cfgForSingleNode(node) ++ cfgForChildren(node)

  private def cfgForSingleNode(node: nodes.CfgNode): Cfg =
    Cfg(Some(node), fringe = List(FringeElement(node, AlwaysEdge)))

  private def cfgForChildren(node: nodes.AstNode): Cfg =
    node.astChildren.l.map(cfgFor).reduceOption((x, y) => x ++ y).getOrElse(Cfg.empty)

  def cfgForAndExpression(call: nodes.Call): Cfg = {
    val leftCfg = cfgFor(call.argument(1))
    val rightCfg = cfgFor(call.argument(2))
    val diffGraph = DiffGraph.newBuilder

    leftCfg.fringe.foreach {
      case FringeElement(l, _) =>
        rightCfg.entryNode.foreach { e =>
          diffGraph.addEdge(l, e, EdgeTypes.CFG)
        }
    }
    Cfg(leftCfg.entryNode,
        diffGraphs = List(diffGraph) ++ leftCfg.diffGraphs ++ rightCfg.diffGraphs,
        fringe = leftCfg.fringe ++ rightCfg.fringe) ++ cfgForSingleNode(call)
  }

  def cfgForOrExpression(call: nodes.Call): Cfg = {
    val leftCfg = cfgFor(call.argument(1))
    val rightCfg = cfgFor(call.argument(2))
    val diffGraph = DiffGraph.newBuilder

    leftCfg.fringe.foreach {
      case FringeElement(l, _) =>
        rightCfg.entryNode.foreach { e =>
          diffGraph.addEdge(l, e, EdgeTypes.CFG)
        }
    }
    Cfg(leftCfg.entryNode,
        diffGraphs = List(diffGraph) ++ leftCfg.diffGraphs ++ rightCfg.diffGraphs,
        fringe = leftCfg.fringe ++ rightCfg.fringe) ++ cfgForSingleNode(call)
  }

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

  private def cfgForConditionalExpression(call: nodes.Call): Cfg = {
    val conditionCfg = cfgFor(call.argument(1))
    val trueCfg = cfgFor(call.argument(2))
    val falseCfg = cfgFor(call.argument(3))
    val diffGraph = DiffGraph.newBuilder

    conditionCfg.fringe.foreach {
      case FringeElement(c, _) =>
        trueCfg.entryNode.foreach { e =>
          diffGraph.addEdge(c, e, EdgeTypes.CFG)
        }
        falseCfg.entryNode.foreach { e =>
          diffGraph.addEdge(c, e, EdgeTypes.CFG)
        }
    }

    Cfg(
      conditionCfg.entryNode,
      diffGraphs = conditionCfg.diffGraphs ++ trueCfg.diffGraphs ++ falseCfg.diffGraphs ++ mutable.ListBuffer(
        diffGraph),
      fringe = trueCfg.fringe ++ falseCfg.fringe
    ) ++ cfgForSingleNode(call)
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

  private def cfgForBreakStatement(node: nodes.ControlStructure): Cfg =
    Cfg(Some(node), breaks = List(node))

  private def cfgForContinueStatement(node: nodes.ControlStructure): Cfg =
    Cfg(Some(node), continues = List(node))

  private def cfgForForStatement(node: nodes.ControlStructure): Cfg = {
    val children = node.astChildren.l
    val initExprCfg = children.find(_.order == 1).map(cfgFor).getOrElse(Cfg.empty)
    val conditionCfg = children.find(_.order == 2).map(cfgFor).getOrElse(Cfg.empty)
    val loopExprCfg = children.find(_.order == 3).map(cfgFor).getOrElse(Cfg.empty)
    val bodyCfg = children.find(_.order == 4).map(cfgFor).getOrElse(Cfg.empty)

    val diffGraph = DiffGraph.newBuilder
    val innerCfg = conditionCfg ++ bodyCfg ++ loopExprCfg

    initExprCfg.fringe.foreach {
      case FringeElement(f, _) =>
        innerCfg.entryNode.foreach { e =>
          diffGraph.addEdge(f, e, EdgeTypes.CFG)
        }
    }

    innerCfg.fringe.foreach {
      case FringeElement(f, _) =>
        innerCfg.entryNode.foreach { e =>
          diffGraph.addEdge(f, e, EdgeTypes.CFG)
        }
    }

    conditionCfg.fringe.foreach {
      case FringeElement(c, _) =>
        bodyCfg.entryNode.foreach { e =>
          diffGraph.addEdge(c, e, EdgeTypes.CFG)
        }
    }

    bodyCfg.continues.foreach { c =>
      loopExprCfg.entryNode match {
        case Some(e) => diffGraph.addEdge(c, e, EdgeTypes.CFG)
        case None =>
          innerCfg.entryNode.foreach { f =>
            diffGraph.addEdge(c, f, EdgeTypes.CFG)
          }
      }
    }

    val entryNode = Option(
      initExprCfg.entryNode.getOrElse(
        conditionCfg.entryNode.getOrElse(
          loopExprCfg.entryNode.getOrElse(
            bodyCfg.entryNode.orNull
          )
        )))

    Cfg(
      entryNode,
      diffGraphs = List(diffGraph) ++ initExprCfg.diffGraphs ++ innerCfg.diffGraphs,
      fringe = conditionCfg.fringe ++ bodyCfg.breaks.map(FringeElement(_, AlwaysEdge))
    )
  }

  private def cfgForDoStatement(node: nodes.ControlStructure): Cfg = {
    val bodyCfg = node.astChildren.filter(_.order(1)).headOption.map(cfgFor).getOrElse(Cfg.empty)
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val diffGraph = DiffGraph.newBuilder

    conditionCfg.fringe.foreach {
      case FringeElement(c, _) =>
        bodyCfg.entryNode
          .map { entry =>
            diffGraph.addEdge(c, entry, EdgeTypes.CFG)
          }
          .getOrElse {
            conditionCfg.entryNode.foreach { cEntry =>
              diffGraph.addEdge(c, cEntry, EdgeTypes.CFG)
            }
          }
    }

    bodyCfg.continues.foreach { c =>
      conditionCfg.entryNode.foreach { e =>
        diffGraph.addEdge(c, e, EdgeTypes.CFG)
      }
    }

    bodyCfg.fringe.foreach {
      case FringeElement(b, _) =>
        conditionCfg.entryNode.foreach { c =>
          diffGraph.addEdge(b, c, EdgeTypes.CFG)
        }
    }

    Cfg(
      if (bodyCfg != Cfg.empty) { bodyCfg.entryNode } else { conditionCfg.entryNode },
      diffGraphs = List(diffGraph) ++ bodyCfg.diffGraphs ++ conditionCfg.diffGraphs,
      fringe = conditionCfg.fringe ++ bodyCfg.breaks.map(FringeElement(_, AlwaysEdge))
    )
  }

  private def cfgForWhileStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val trueCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)

    val diffGraph = DiffGraph.newBuilder
    conditionCfg.fringe.foreach {
      case FringeElement(condition, _) =>
        trueCfg.entryNode.foreach { trueEntry =>
          diffGraph.addEdge(condition, trueEntry, EdgeTypes.CFG)
        }
    }

    trueCfg.fringe.foreach {
      case FringeElement(lastNode, _) =>
        conditionCfg.entryNode.foreach { entry =>
          diffGraph.addEdge(lastNode, entry, EdgeTypes.CFG)
        }
    }

    trueCfg.continues.foreach { b =>
      conditionCfg.entryNode.foreach { c =>
        diffGraph.addEdge(b, c, EdgeTypes.CFG)
      }
    }

    Cfg(
      conditionCfg.entryNode,
      diffGraphs = List(diffGraph) ++ conditionCfg.diffGraphs ++ trueCfg.diffGraphs,
      fringe = conditionCfg.fringe ++ trueCfg.breaks.map(FringeElement(_, AlwaysEdge))
    )
  }

  private def cfgForSwitchStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val bodyCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)

    val diffGraph = DiffGraph.newBuilder
    bodyCfg.caseLabels.foreach { caseNode =>
      conditionCfg.fringe.foreach {
        case FringeElement(c, _) =>
          diffGraph.addEdge(c, caseNode, EdgeTypes.CFG)
      }
    }

    val hasDefaultCase = bodyCfg.caseLabels.exists(x => x.asInstanceOf[nodes.JumpTarget].name == "default")

    Cfg(
      conditionCfg.entryNode,
      diffGraphs = List(diffGraph) ++ conditionCfg.diffGraphs ++ bodyCfg.diffGraphs,
      fringe = { if (!hasDefaultCase) { conditionCfg.fringe } else { List() } } ++ bodyCfg.breaks.map(
        FringeElement(_, AlwaysEdge)) ++ bodyCfg.fringe
    )
  }

  private def cfgForIfStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val trueCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val falseCfg = node.start.whenFalse.headOption.map(cfgFor).getOrElse(Cfg.empty)

    val diffGraph = DiffGraph.newBuilder

    conditionCfg.fringe.foreach {
      case FringeElement(condition, _) =>
        trueCfg.entryNode.foreach { trueEntry =>
          diffGraph.addEdge(condition, trueEntry, EdgeTypes.CFG)
        }
        falseCfg.entryNode.foreach { falseEntry =>
          diffGraph.addEdge(condition, falseEntry, EdgeTypes.CFG)
        }
    }

    Cfg(
      conditionCfg.entryNode,
      diffGraphs = List(diffGraph) ++ conditionCfg.diffGraphs ++ trueCfg.diffGraphs ++ falseCfg.diffGraphs,
      fringe = trueCfg.fringe ++ {
        if (falseCfg.entryNode.isDefined) {
          falseCfg.fringe
        } else {
          conditionCfg.fringe
        }
      }
    )
  }

  private def cfgForJumpTarget(n: nodes.JumpTarget): Cfg = {
    val labelName = n.name
    val cfg = cfgForSingleNode(n)
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      cfg.copy(caseLabels = List(n))
    } else {
      cfg.copy(labeledNodes = cfg.labeledNodes + (labelName -> n))
    }
  }

  private def cfgForGotoStatement(node: nodes.ControlStructure): Cfg = {
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.map(t => Cfg(Some(node), gotos = List((node, t)))).getOrElse(Cfg.empty)
  }

  private def cfgForReturn(actualRet: nodes.Return): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    diffGraph.addEdge(actualRet, exitNode, EdgeTypes.CFG)
    cfgForChildren(actualRet) ++ Cfg(Some(actualRet), List(diffGraph), fringe = List())
  }

}

object CfgCreatorForMethod {

  case class FringeElement(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType)

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
