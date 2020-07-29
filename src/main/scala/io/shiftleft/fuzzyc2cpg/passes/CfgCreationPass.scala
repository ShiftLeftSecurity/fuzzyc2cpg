package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.MethodReturn
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, nodes}
import io.shiftleft.fuzzyc2cpg.passes.CfgCreatorForMethod.FringeElement
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

import scala.collection.mutable

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
               var diffGraphs: mutable.ListBuffer[DiffGraph.Builder] = mutable.ListBuffer(DiffGraph.newBuilder),
               var fringe: List[FringeElement] = List(),
               var labeledNodes: Map[String, nodes.CfgNode] = Map(),
               var returns: List[nodes.CfgNode] = List(),
               var markerStack: List[Option[nodes.CfgNode]] = List(),
               breakStack: List[nodes.CfgNode] = List(),
               continueStack: List[nodes.CfgNode] = List(),
               caseStack: List[nodes.CfgNode] = List(),
               var gotos: List[(nodes.CfgNode, String)] = List()) {

  import CfgCreatorForMethod._

  private val logger = LoggerFactory.getLogger(getClass)

  def withFringeConnectedTo(node: nodes.CfgNode): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    fringe.foreach {
      case FringeElement(srcNode, _) =>
        // TODO add edge CFG edge type in CPG spec
        // val props = List(("CFG_EDGE_TYPE", cfgEdgeType.toString))
        diffGraph.addEdge(
          srcNode,
          node,
          EdgeTypes.CFG
        )
    }
    fringe = Nil.add(node, AlwaysEdge)
    diffGraphs = diffGraphs ++ List(diffGraph)
    this.copy()
  }

  def withResolvedGotos(): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    gotos.foreach {
      case (goto, label) =>
        labeledNodes.get(label) match {
          case Some(labeledNode) =>
            // TODO: CFG_EDGE_TYPE isn't defined for non-proto CPGs
            // .addProperty(EdgeProperty.CFG_EDGE_TYPE, AlwaysEdge.toString)
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
        breakStack = this.breakStack ++ other.breakStack,
        continueStack = this.continueStack ++ other.continueStack,
        caseStack = this.caseStack ++ other.caseStack
      )
    }
  }

}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  import CfgCreatorForMethod._

  private val exitNode: MethodReturn = entryNode.methodReturn

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

  private def cfgFor(node: nodes.AstNode): Cfg =
    node match {
      case n: nodes.ControlStructure =>
        cfgForControlStructure(n)
      case n: nodes.JumpTarget =>
        cfgForJumpTarget(n)
      case actualRet: nodes.Return => cfgForReturn(actualRet)
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

  private def cfgForBreakStatement(node: nodes.ControlStructure): Cfg =
    Cfg(Some(node), breakStack = List(node))

  private def cfgForContinueStatement(node: nodes.ControlStructure): Cfg =
    Cfg(Some(node), continueStack = List(node))

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

    bodyCfg.continueStack.foreach { c =>
      loopExprCfg.entryNode
        .map { e =>
          diffGraph.addEdge(c, e, EdgeTypes.CFG)
        }
        .getOrElse(
          innerCfg.entryNode.map { f =>
            diffGraph.addEdge(c, f, EdgeTypes.CFG)
          }
        )
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
      diffGraphs = mutable
        .ListBuffer(diffGraph) ++ initExprCfg.diffGraphs ++ innerCfg.diffGraphs,
      fringe = conditionCfg.fringe ++ bodyCfg.breakStack.map(FringeElement(_, AlwaysEdge))
    )
  }

  //  private def handleForStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
  //    initialCfg.breakStack.pushLayer()
  //    initialCfg.continueStack.pushLayer()
  //
  //    val children = node.astChildren.l
  //    val initExprOption = children.find(_.order == 1)
  //    val conditionOption = children.find(_.order == 2)
  //    val loopExprOption = children.find(_.order == 3)
  //    val statementOption = children.find(_.order == 4)
  //
  //    val cfg1 = initExprOption.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))
  //
  //    cfg1.markerStack = None :: cfg1.markerStack
  //    val (conditionFringe, cfg3) =
  //      conditionOption match {
  //        case Some(condition) =>
  //          val cfg2 = convert(condition, cfg1)
  //          val storedFringe = cfg2.fringe
  //          cfg2.fringe = cfg2.fringe.setCfgEdgeType(TrueEdge)
  //          (storedFringe, cfg2)
  //        case None => (Nil, cfg1)
  //      }
  //
  //    val cfg4 = statementOption.foldLeft(cfg3)((cfg, child) => convert(child, cfg))
  //
  //    cfg4.fringe = cfg4.fringe.add(cfg4.continueStack.getTopElements, AlwaysEdge)
  //
  //    val cfg5 = loopExprOption.foldLeft(cfg4)((cfg, child) => convert(child, cfg))
  //    val cfg6 = cfg5.markerStack.head.foldLeft(cfg5)((cfg, child) => extendCfg(child, cfg))
  //
  //    cfg6.fringe = conditionFringe
  //      .setCfgEdgeType(FalseEdge)
  //      .add(initialCfg.breakStack.getTopElements, AlwaysEdge)
  //
  //    cfg6.markerStack = cfg6.markerStack.tail
  //    cfg6.breakStack.popLayer()
  //    cfg6.continueStack.popLayer()
  //    cfg6
  //  }

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

    bodyCfg.continueStack.foreach { c =>
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
      diffGraphs = mutable.ListBuffer(diffGraph) ++ bodyCfg.diffGraphs ++ conditionCfg.diffGraphs,
      fringe = conditionCfg.fringe ++ bodyCfg.breakStack.map(FringeElement(_, AlwaysEdge))
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

    trueCfg.continueStack.foreach { b =>
      conditionCfg.entryNode.foreach { c =>
        diffGraph.addEdge(b, c, EdgeTypes.CFG)
      }
    }

    Cfg(
      conditionCfg.entryNode,
      diffGraphs = mutable.ListBuffer(diffGraph) ++ conditionCfg.diffGraphs ++ trueCfg.diffGraphs,
      fringe = conditionCfg.fringe ++ trueCfg.breakStack.map(FringeElement(_, AlwaysEdge))
    )
  }

  private def cfgForSwitchStatement(node: nodes.ControlStructure): Cfg = {
    val conditionCfg = node.start.condition.headOption.map(cfgFor).getOrElse(Cfg.empty)
    val bodyCfg = node.start.whenTrue.headOption.map(cfgFor).getOrElse(Cfg.empty)

    val diffGraph = DiffGraph.newBuilder
    bodyCfg.caseStack.foreach { caseNode =>
      conditionCfg.fringe.foreach {
        case FringeElement(c, _) =>
          diffGraph.addEdge(c, caseNode, EdgeTypes.CFG)
      }
    }

    val hasDefaultCase = bodyCfg.caseStack.exists(x => x.asInstanceOf[nodes.JumpTarget].name == "default")

    Cfg(
      conditionCfg.entryNode,
      diffGraphs = mutable.ListBuffer(diffGraph) ++ conditionCfg.diffGraphs ++ bodyCfg.diffGraphs,
      fringe = { if (!hasDefaultCase) { conditionCfg.fringe } else { List() } } ++ bodyCfg.breakStack.map(
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
      diffGraphs = mutable
        .ListBuffer(diffGraph) ++ conditionCfg.diffGraphs ++ trueCfg.diffGraphs ++ falseCfg.diffGraphs,
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
      cfg.copy(caseStack = List(n))
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
    cfgForChildren(actualRet) ++ Cfg(Some(actualRet), mutable.ListBuffer(diffGraph), fringe = List())
  }

//  private def convert(node: nodes.AstNode, initialCfg: Cfg): Cfg = {
//    node match {
//      case n: nodes.ControlStructure =>
//        convertControlStructure(n, initialCfg)
//      case call: nodes.Call if call.name == Operators.conditional =>
//        convertConditionalExpression(call, initialCfg)
//      case call: nodes.Call if call.name == Operators.logicalAnd =>
//        convertAndExpression(call, initialCfg)
//      case call: nodes.Call if call.name == Operators.logicalOr =>
//        convertOrExpression(call, initialCfg)
//      case n: nodes.AstNode =>
//        convertChildren(n, initialCfg)
//    }
//  }
//
//  private def convertConditionalExpression(call: nodes.Call, initialCfg: Cfg): Cfg = {
//    val condition = call.argument(1)
//    val trueExpression = call.argument(2)
//    val falseExpression = call.argument(3)
//
//    val cfg1 = convert(condition, initialCfg)
//    val fromCond = cfg1.fringe
//    cfg1.fringe = cfg1.fringe.setCfgEdgeType(TrueEdge)
//    val cfg2 = convert(trueExpression, cfg1)
//    val fromTrue = cfg2.fringe
//    cfg2.fringe = fromCond.setCfgEdgeType(FalseEdge)
//    val cfg3 = convert(falseExpression, cfg2)
//    cfg3.fringe = cfg3.fringe ++ fromTrue
//    extendCfg(call, cfg3)
//  }
//
//  private def convertAndExpression(call: Call, initialCfg: Cfg): Cfg = {
//    val cfg1 = convert(call.argument(1), initialCfg)
//    val entry = cfg1.fringe
//    cfg1.fringe = cfg1.fringe.setCfgEdgeType(TrueEdge)
//    val cfg2 = convert(call.argument(2), cfg1)
//    cfg2.fringe = cfg2.fringe ++ entry.setCfgEdgeType(FalseEdge)
//    extendCfg(call, cfg2)
//  }
//
//  private def convertOrExpression(call: Call, initialCfg: Cfg): Cfg = {
//    val left = call.argument(1)
//    val right = call.argument(2)
//    val cfg1 = convert(left, initialCfg)
//    val entry = cfg1.fringe
//    cfg1.fringe = cfg1.fringe.setCfgEdgeType(FalseEdge)
//    val cfg2 = convert(right, cfg1)
//    cfg2.fringe ++= entry.setCfgEdgeType(TrueEdge)
//    extendCfg(call, cfg2)
//  }

}

object CfgCreatorForMethod {
  implicit class FringeWrapper(fringe: List[FringeElement]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      fringe.map {
        case FringeElement(node, _) =>
          FringeElement(node, cfgEdgeType)
      }
    }
    def add(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType): List[FringeElement] =
      FringeElement(node, cfgEdgeType) :: fringe

    def add(ns: List[nodes.CfgNode], cfgEdgeType: CfgEdgeType): List[FringeElement] =
      ns.map(node => FringeElement(node, cfgEdgeType)) ++ fringe

  }

  case class FringeElement(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType)

}
