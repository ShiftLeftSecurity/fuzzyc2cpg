package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Call
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.passes.CfgCreatorForMethod.FringeElement
import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.LayeredStack
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

case class Cfg(entryNode: nodes.CfgNode,
               var diffGraphs: mutable.ListBuffer[DiffGraph.Builder] = mutable.ListBuffer(DiffGraph.newBuilder),
               var fringe: List[FringeElement] = List(),
               var labeledNodes: Map[String, nodes.CfgNode] = Map(),
               var returns: List[nodes.CfgNode] = List(),
               var markerStack: List[Option[nodes.CfgNode]] = List(),
               breakStack: LayeredStack = new LayeredStack(),
               continueStack: LayeredStack = new LayeredStack(),
               caseStack: LayeredStack = new LayeredStack(),
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

}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  import CfgCreatorForMethod._

  def run(): Iterator[DiffGraph] =
    convertMethod(entryNode).withResolvedGotos().diffGraphs.map(_.build).iterator

  private def convertMethod(node: nodes.Method): Cfg =
    convertChildren(node, Cfg(node, fringe = List(FringeElement(node, AlwaysEdge))))

  private def convertChildren(node: nodes.AstNode, initialCfg: Cfg): Cfg =
    node.astChildren.l.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))

  private def convert(node: nodes.AstNode, initialCfg: Cfg): Cfg = {
    node match {
      case n: nodes.ControlStructure =>
        convertControlStructure(n, initialCfg)
      case n: nodes.JumpTarget =>
        convertJumpTarget(n, initialCfg)
      case call: nodes.Call if call.name == Operators.conditional =>
        convertConditionalExpression(call, initialCfg)
      case call: nodes.Call if call.name == Operators.logicalAnd =>
        convertAndExpression(call, initialCfg)
      case call: nodes.Call if call.name == Operators.logicalOr =>
        convertOrExpression(call, initialCfg)
      case actualRet: nodes.Return =>
        convertReturn(actualRet, initialCfg)
      case (_: nodes.Call | _: nodes.Identifier | _: nodes.Literal | _: nodes.MethodReturn) =>
        extendCfg(node.asInstanceOf[nodes.CfgNode], convertChildren(node, initialCfg))
      case n: nodes.AstNode =>
        convertChildren(n, initialCfg)
    }
  }

  private def convertReturn(actualRet: nodes.Return, initialCfg: Cfg): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    val cfg = extendCfg(actualRet, convertChildren(actualRet, initialCfg))
    cfg.fringe = Nil
    diffGraph.addEdge(
      actualRet,
      entryNode.methodReturn,
      EdgeTypes.CFG
    )
    cfg.diffGraphs += diffGraph
    cfg
  }

  private def convertJumpTarget(n: nodes.JumpTarget, initialCfg: Cfg): Cfg = {
    val labelName = n.name
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      // Under normal conditions this is always true.
      // But if the parser missed a switch statement, caseStack
      // might be empty.
      if (initialCfg.caseStack.numberOfLayers > 0) {
        initialCfg.caseStack.store(n)
      }
    } else {
      initialCfg.labeledNodes = initialCfg.labeledNodes + (labelName -> n)
    }
    extendCfg(n, initialCfg)
  }

  private def convertConditionalExpression(call: nodes.Call, initialCfg: Cfg): Cfg = {
    val condition = call.argument(1)
    val trueExpression = call.argument(2)
    val falseExpression = call.argument(3)

    val cfg1 = convert(condition, initialCfg)
    val fromCond = cfg1.fringe
    cfg1.fringe = cfg1.fringe.setCfgEdgeType(TrueEdge)
    val cfg2 = convert(trueExpression, cfg1)
    val fromTrue = cfg2.fringe
    cfg2.fringe = fromCond.setCfgEdgeType(FalseEdge)
    val cfg3 = convert(falseExpression, cfg2)
    cfg3.fringe = cfg3.fringe ++ fromTrue
    extendCfg(call, cfg3)
  }

  private def convertAndExpression(call: Call, initialCfg: Cfg): Cfg = {
    val cfg1 = convert(call.argument(1), initialCfg)
    val entry = cfg1.fringe
    cfg1.fringe = cfg1.fringe.setCfgEdgeType(TrueEdge)
    val cfg2 = convert(call.argument(2), cfg1)
    cfg2.fringe = cfg2.fringe ++ entry.setCfgEdgeType(FalseEdge)
    extendCfg(call, cfg2)
  }

  private def convertOrExpression(call: Call, initialCfg: Cfg): Cfg = {
    val left = call.argument(1)
    val right = call.argument(2)
    val cfg1 = convert(left, initialCfg)
    val entry = cfg1.fringe
    cfg1.fringe = cfg1.fringe.setCfgEdgeType(FalseEdge)
    val cfg2 = convert(right, cfg1)
    cfg2.fringe ++= entry.setCfgEdgeType(TrueEdge)
    extendCfg(call, cfg2)
  }

  private def handleBreakStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    val cfg = extendCfg(node, initialCfg)
    // Under normal conditions this is always true.
    // But if the parser missed a loop or switch statement, breakStack
    // might be empty.
    if (cfg.breakStack.numberOfLayers > 0) {
      cfg.fringe = Nil
      cfg.breakStack.store(node)
    }
    cfg
  }

  private def handleContinueStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    val cfg = extendCfg(node, initialCfg)
    // Under normal conditions this is always true.
    // But if the parser missed a loop statement, continueStack
    // might be empty.
    if (cfg.continueStack.numberOfLayers > 0) {
      cfg.fringe = Nil
      cfg.continueStack.store(node)
    }
    cfg
  }

  private def handleWhileStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    initialCfg.breakStack.pushLayer()
    initialCfg.continueStack.pushLayer()

    initialCfg.markerStack = None :: initialCfg.markerStack
    val cfg1 = node.start.condition.headOption.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))
    val conditionFringe = cfg1.fringe
    cfg1.fringe = cfg1.fringe.setCfgEdgeType(TrueEdge)

    val cfg2 = node.start.whenTrue.l.foldLeft(cfg1)((cfg, child) => convert(child, cfg))
    cfg2.fringe = cfg2.fringe.add(cfg2.continueStack.getTopElements, AlwaysEdge)
    val cfg3 = extendCfg(cfg2.markerStack.head.get, cfg2)

    cfg3.fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(cfg3.breakStack.getTopElements, AlwaysEdge)

    cfg3.markerStack = cfg3.markerStack.tail
    cfg3.breakStack.popLayer()
    cfg3.continueStack.popLayer()
    cfg3
  }

  private def handleDoStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    initialCfg.breakStack.pushLayer()
    initialCfg.continueStack.pushLayer()

    initialCfg.markerStack = None :: initialCfg.markerStack
    val cfg1 = node.astChildren.filter(_.order(1)).foldLeft(initialCfg)((cfg, child) => convert(child, cfg))
    cfg1.fringe = cfg1.fringe.add(cfg1.continueStack.getTopElements, AlwaysEdge)

    val cfg4 = node.start.condition.headOption match {
      case Some(condition) =>
        val cfg2 = convert(condition, cfg1)
        val conditionFringe = cfg2.fringe
        cfg2.fringe = cfg2.fringe.setCfgEdgeType(TrueEdge)

        val cfg3 = extendCfg(cfg2.markerStack.head.get, cfg2)

        cfg3.fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        cfg3
      case None =>
        // We only get here if the parser missed the condition.
        // In this case doing nothing here means that we have
        // no CFG edge to the loop start because we default
        // to an always false condition.
        cfg1
    }

    cfg4.fringe = cfg4.fringe.add(initialCfg.breakStack.getTopElements, AlwaysEdge)

    cfg4.markerStack = cfg4.markerStack.tail
    cfg4.breakStack.popLayer()
    cfg4.continueStack.popLayer()
    cfg4
  }

  private def handleForStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    initialCfg.breakStack.pushLayer()
    initialCfg.continueStack.pushLayer()

    val children = node.astChildren.l
    val initExprOption = children.find(_.order == 1)
    val conditionOption = children.find(_.order == 2)
    val loopExprOption = children.find(_.order == 3)
    val statementOption = children.find(_.order == 4)

    val cfg1 = initExprOption.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))

    cfg1.markerStack = None :: cfg1.markerStack
    val (conditionFringe, cfg3) =
      conditionOption match {
        case Some(condition) =>
          val cfg2 = convert(condition, cfg1)
          val storedFringe = cfg2.fringe
          cfg2.fringe = cfg2.fringe.setCfgEdgeType(TrueEdge)
          (storedFringe, cfg2)
        case None => (Nil, cfg1)
      }

    val cfg4 = statementOption.foldLeft(cfg3)((cfg, child) => convert(child, cfg))

    cfg4.fringe = cfg4.fringe.add(cfg4.continueStack.getTopElements, AlwaysEdge)

    val cfg5 = loopExprOption.foldLeft(cfg4)((cfg, child) => convert(child, cfg))
    val cfg6 = cfg5.markerStack.head.foldLeft(cfg5)((cfg, child) => extendCfg(child, cfg))

    cfg6.fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(initialCfg.breakStack.getTopElements, AlwaysEdge)

    cfg6.markerStack = cfg6.markerStack.tail
    cfg6.breakStack.popLayer()
    cfg6.continueStack.popLayer()
    cfg6
  }

  private def handleGotoStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    val cfg = extendCfg(node, initialCfg)
    cfg.fringe = Nil
    // TODO: the target name should be in the AST
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.foreach { target =>
      cfg.gotos = (node, target) :: cfg.gotos
    }
    cfg
  }

  private def handleIfStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    val cfg1 = node.start.condition.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))
    val conditionFringe = cfg1.fringe
    cfg1.fringe = cfg1.fringe.setCfgEdgeType(TrueEdge)
    val cfg2 = node.start.whenTrue.foldLeft(cfg1)((cfg, child) => convert(child, cfg))

    val cfg4 = node.start.whenFalse
      .map { elseStatement =>
        val ifBlockFringe = cfg2.fringe
        cfg2.fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        val cfg3 = convert(elseStatement, cfg2)
        cfg3.fringe = cfg3.fringe ++ ifBlockFringe
        cfg3
      }
      .headOption
      .getOrElse {
        cfg2.fringe = cfg2.fringe ++ conditionFringe.setCfgEdgeType(FalseEdge)
        cfg2
      }
    cfg4
  }

  private def handleSwitchStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    val cfg1 = node.start.condition.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))
    val conditionFringe = cfg1.fringe.setCfgEdgeType(CaseEdge)
    cfg1.fringe = Nil

    // We can only push the break and case stacks after we processed the condition
    // in order to allow for nested switches with no nodes CFG nodes in between
    // an outer switch case label and the inner switch condition.
    // This is ok because in C/C++ it is not allowed to have another switch
    // statement in the condition of a switch statement.
    cfg1.breakStack.pushLayer()
    cfg1.caseStack.pushLayer()

    val cfg2 = node.start.whenTrue.foldLeft(cfg1)((cfg, child) => convert(child, cfg))
    val switchFringe = cfg2.fringe

    val cfg3 = cfg2.caseStack.getTopElements.foldLeft(cfg2) { (cfg, caseNode) =>
      cfg.fringe = conditionFringe
      extendCfg(caseNode, cfg)
    }

    val hasDefaultCase = cfg3.caseStack.getTopElements.exists { caseNode =>
      caseNode.asInstanceOf[nodes.JumpTarget].name == "default"
    }

    cfg3.fringe = switchFringe.add(cfg3.breakStack.getTopElements, AlwaysEdge)

    if (!hasDefaultCase) {
      cfg3.fringe = cfg3.fringe ++ conditionFringe
    }

    cfg3.breakStack.popLayer()
    cfg3.caseStack.popLayer()
    cfg3
  }

  private def convertControlStructure(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    node.parserTypeName match {
      case "BreakStatement" =>
        handleBreakStatement(node, initialCfg)
      case "ContinueStatement" =>
        handleContinueStatement(node, initialCfg)
      case "WhileStatement" =>
        handleWhileStatement(node, initialCfg)
      case "DoStatement" =>
        handleDoStatement(node, initialCfg)
      case "ForStatement" =>
        handleForStatement(node, initialCfg)
      case "GotoStatement" =>
        handleGotoStatement(node, initialCfg)
      case "IfStatement" =>
        handleIfStatement(node, initialCfg)
      case "ElseStatement" =>
        convertChildren(node, initialCfg)
      case "SwitchStatement" =>
        handleSwitchStatement(node, initialCfg)
      case _ =>
        initialCfg
    }
  }

  private def extendCfg(dstNode: nodes.CfgNode, initialCfg: Cfg): Cfg = {
    val cfg = initialCfg.withFringeConnectedTo(dstNode)
    if (cfg.markerStack.nonEmpty) {
      // Up until the first none None stack element we replace the Nones with Some(dstNode)
      val leadingNoneLength = cfg.markerStack.segmentLength(_.isEmpty, 0)
      cfg.markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ cfg.markerStack
        .drop(leadingNoneLength)
    }
    cfg
  }

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
