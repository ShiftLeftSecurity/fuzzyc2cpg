package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Call
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
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

case class Cfg(entryNode: nodes.CfgNode) {

  import CfgCreatorForMethod._
  private val logger = LoggerFactory.getLogger(getClass)

  var diffGraphs: mutable.ListBuffer[DiffGraph.Builder] = mutable.ListBuffer(DiffGraph.newBuilder)

  var fringe = List[FringeElement]().add(entryNode, AlwaysEdge)

  var labeledNodes = Map[String, nodes.CfgNode]()
  var returns = List[nodes.CfgNode]()

  var markerStack = List[Option[nodes.CfgNode]]()
  val breakStack = new LayeredStack()
  val continueStack = new LayeredStack()
  val caseStack = new LayeredStack()
  var gotos = List[(nodes.CfgNode, String)]()

  def connectGotoAndLabels(): Cfg = {
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
    diffGraphs += diffGraph
    this
  }

}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  import CfgCreatorForMethod._

  def run(): Iterator[DiffGraph] = {
    val cfg = convertMethod(entryNode).connectGotoAndLabels()
    cfg.diffGraphs.map(_.build).iterator
  }

  private def convertMethod(node: nodes.Method): Cfg = {
    expandChildren(node, Cfg(node))
  }

  private def expandChildren(node: nodes.AstNode, initialCfg: Cfg): Cfg = {
    node.astChildren.l.foldLeft(initialCfg)((cfg, child) => convert(child, cfg))
  }

  private def convert(node: nodes.AstNode, initialCfg: Cfg): Cfg = {
    node match {
      case n: nodes.Method =>
        convertMethod(n)
      case n: nodes.ControlStructure =>
        handleControlStructure(n, initialCfg)
      case n: nodes.JumpTarget =>
        handleJumpTarget(n, initialCfg)
      case call: nodes.Call if call.name == Operators.conditional =>
        handleConditionalExpression(call, initialCfg)
      case call: nodes.Call if call.name == Operators.logicalAnd =>
        handleAndExpression(call, initialCfg)
      case call: nodes.Call if call.name == Operators.logicalOr =>
        handleOrExpression(call, initialCfg)
      case actualRet: nodes.Return =>
        handleReturn(actualRet, initialCfg)
      case (_: nodes.Call | _: nodes.Identifier | _: nodes.Literal | _: nodes.MethodReturn) =>
        expandChildrenAndInclude(node.asInstanceOf[nodes.CfgNode], initialCfg)
      case n: nodes.AstNode =>
        expandChildren(n, initialCfg)
    }
  }

  private def expandChildrenAndInclude(node: nodes.CfgNode, initialCfg: Cfg): Cfg = {
    expandChildren(node, initialCfg)
    extendCfg(node, initialCfg)
  }

  private def handleReturn(actualRet: nodes.Return, initialCfg: Cfg): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    expandChildren(actualRet, initialCfg)
    extendCfg(actualRet, initialCfg)
    initialCfg.fringe = Nil
    diffGraph.addEdge(
      actualRet,
      entryNode.methodReturn,
      EdgeTypes.CFG
    )
    initialCfg.diffGraphs += diffGraph
    initialCfg
  }

  private def handleJumpTarget(n: nodes.JumpTarget, initialCfg: Cfg): Cfg = {
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

  private def handleConditionalExpression(call: nodes.Call, initialCfg: Cfg): Cfg = {
    val condition = call.argument(1)
    val trueExpression = call.argument(2)
    val falseExpression = call.argument(3)

    convert(condition, initialCfg)
    val fromCond = initialCfg.fringe
    initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(TrueEdge)
    convert(trueExpression, initialCfg)
    val fromTrue = initialCfg.fringe
    initialCfg.fringe = fromCond.setCfgEdgeType(FalseEdge)
    convert(falseExpression, initialCfg)
    initialCfg.fringe = initialCfg.fringe ++ fromTrue
    extendCfg(call, initialCfg)
  }

  private def handleAndExpression(call: Call, initialCfg: Cfg): Cfg = {
    convert(call.argument(1), initialCfg)
    val entry = initialCfg.fringe
    initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(TrueEdge)
    convert(call.argument(2), initialCfg)
    initialCfg.fringe = initialCfg.fringe ++ entry.setCfgEdgeType(FalseEdge)
    extendCfg(call, initialCfg)
  }

  private def handleOrExpression(call: Call, initialCfg: Cfg): Cfg = {
    val left = call.argument(1)
    val right = call.argument(2)
    convert(left, initialCfg)
    val entry = initialCfg.fringe
    initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(FalseEdge)
    convert(right, initialCfg)
    initialCfg.fringe = initialCfg.fringe ++ entry.setCfgEdgeType(TrueEdge)
    extendCfg(call, initialCfg)
  }

  private def handleBreakStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    extendCfg(node, initialCfg)
    // Under normal conditions this is always true.
    // But if the parser missed a loop or switch statement, breakStack
    // might be empty.
    if (initialCfg.breakStack.numberOfLayers > 0) {
      initialCfg.fringe = Nil
      initialCfg.breakStack.store(node)
    }
    initialCfg
  }

  private def handleContinueStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    extendCfg(node, initialCfg)
    // Under normal conditions this is always true.
    // But if the parser missed a loop statement, continueStack
    // might be empty.
    if (initialCfg.continueStack.numberOfLayers > 0) {
      initialCfg.fringe = Nil
      initialCfg.continueStack.store(node)
    }
    initialCfg
  }

  private def handleWhileStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    initialCfg.breakStack.pushLayer()
    initialCfg.continueStack.pushLayer()

    initialCfg.markerStack = None :: initialCfg.markerStack
    node.start.condition.headOption.foreach(convert(_, initialCfg))
    val conditionFringe = initialCfg.fringe
    initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(TrueEdge)

    node.start.whenTrue.l.foreach(convert(_, initialCfg))
    initialCfg.fringe = initialCfg.fringe.add(initialCfg.continueStack.getTopElements, AlwaysEdge)
    extendCfg(initialCfg.markerStack.head.get, initialCfg)

    initialCfg.fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(initialCfg.breakStack.getTopElements, AlwaysEdge)

    initialCfg.markerStack = initialCfg.markerStack.tail
    initialCfg.breakStack.popLayer()
    initialCfg.continueStack.popLayer()
    initialCfg
  }

  private def handleDoStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    initialCfg.breakStack.pushLayer()
    initialCfg.continueStack.pushLayer()

    initialCfg.markerStack = None :: initialCfg.markerStack
    node.astChildren.filter(_.order(1)).foreach(convert(_, initialCfg))
    initialCfg.fringe = initialCfg.fringe.add(initialCfg.continueStack.getTopElements, AlwaysEdge)

    node.start.condition.headOption match {
      case Some(condition) =>
        convert(condition, initialCfg)
        val conditionFringe = initialCfg.fringe
        initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(TrueEdge)

        extendCfg(initialCfg.markerStack.head.get, initialCfg)

        initialCfg.fringe = conditionFringe.setCfgEdgeType(FalseEdge)
      case None =>
      // We only get here if the parser missed the condition.
      // In this case doing nothing here means that we have
      // no CFG edge to the loop start because we default
      // to an always false condition.
    }
    initialCfg.fringe = initialCfg.fringe.add(initialCfg.breakStack.getTopElements, AlwaysEdge)

    initialCfg.markerStack = initialCfg.markerStack.tail
    initialCfg.breakStack.popLayer()
    initialCfg.continueStack.popLayer()
    initialCfg
  }

  private def handleForStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    initialCfg.breakStack.pushLayer()
    initialCfg.continueStack.pushLayer()

    val children = node.astChildren.l
    val initExprOption = children.find(_.order == 1)
    val conditionOption = children.find(_.order == 2)
    val loopExprOption = children.find(_.order == 3)
    val statementOption = children.find(_.order == 4)

    initExprOption.foreach(convert(_, initialCfg))

    initialCfg.markerStack = None :: initialCfg.markerStack
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          convert(condition, initialCfg)
          val storedFringe = initialCfg.fringe
          initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None => Nil
      }

    statementOption.foreach(convert(_, initialCfg))

    initialCfg.fringe = initialCfg.fringe.add(initialCfg.continueStack.getTopElements, AlwaysEdge)

    loopExprOption.foreach(convert(_, initialCfg))

    initialCfg.markerStack.head.foreach(extendCfg(_, initialCfg))

    initialCfg.fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(initialCfg.breakStack.getTopElements, AlwaysEdge)

    initialCfg.markerStack = initialCfg.markerStack.tail
    initialCfg.breakStack.popLayer()
    initialCfg.continueStack.popLayer()
    initialCfg
  }

  private def handleGotoStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    extendCfg(node, initialCfg)
    initialCfg.fringe = Nil
    // TODO: the target name should be in the AST
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.foreach { target =>
      initialCfg.gotos = (node, target) :: initialCfg.gotos
    }
    initialCfg
  }

  private def handleIfStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    node.start.condition.foreach(convert(_, initialCfg))
    val conditionFringe = initialCfg.fringe
    initialCfg.fringe = initialCfg.fringe.setCfgEdgeType(TrueEdge)
    node.start.whenTrue.foreach(convert(_, initialCfg))
    node.start.whenFalse
      .map { elseStatement =>
        val ifBlockFringe = initialCfg.fringe
        initialCfg.fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        convert(elseStatement, initialCfg)
        initialCfg.fringe = initialCfg.fringe ++ ifBlockFringe
      }
      .headOption
      .getOrElse {
        initialCfg.fringe = initialCfg.fringe ++ conditionFringe.setCfgEdgeType(FalseEdge)
      }
    initialCfg
  }

  private def handleSwitchStatement(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
    node.start.condition.foreach(convert(_, initialCfg))
    val conditionFringe = initialCfg.fringe.setCfgEdgeType(CaseEdge)
    initialCfg.fringe = Nil

    // We can only push the break and case stacks after we processed the condition
    // in order to allow for nested switches with no nodes CFG nodes in between
    // an outer switch case label and the inner switch condition.
    // This is ok because in C/C++ it is not allowed to have another switch
    // statement in the condition of a switch statement.
    initialCfg.breakStack.pushLayer()
    initialCfg.caseStack.pushLayer()

    node.start.whenTrue.foreach(convert(_, initialCfg))
    val switchFringe = initialCfg.fringe

    initialCfg.caseStack.getTopElements.foreach { caseNode =>
      initialCfg.fringe = conditionFringe
      extendCfg(caseNode, initialCfg)
    }

    val hasDefaultCase = initialCfg.caseStack.getTopElements.exists { caseNode =>
      caseNode.asInstanceOf[nodes.JumpTarget].name == "default"
    }

    initialCfg.fringe = switchFringe.add(initialCfg.breakStack.getTopElements, AlwaysEdge)

    if (!hasDefaultCase) {
      initialCfg.fringe = initialCfg.fringe ++ conditionFringe
    }

    initialCfg.breakStack.popLayer()
    initialCfg.caseStack.popLayer()
    initialCfg
  }

  private def handleControlStructure(node: nodes.ControlStructure, initialCfg: Cfg): Cfg = {
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
        expandChildren(node, initialCfg)
      case "SwitchStatement" =>
        handleSwitchStatement(node, initialCfg)
      case _ =>
    }
    initialCfg
  }

  private def extendCfg(dstNode: nodes.CfgNode, initialCfg: Cfg): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    initialCfg.fringe.foreach {
      case FringeElement(srcNode, _) =>
        // TODO add edge CFG edge type in CPG spec
        // val props = List(("CFG_EDGE_TYPE", cfgEdgeType.toString))
        diffGraph.addEdge(
          srcNode,
          dstNode,
          EdgeTypes.CFG
        )
    }
    initialCfg.fringe = Nil.add(dstNode, AlwaysEdge)

    if (initialCfg.markerStack.nonEmpty) {
      // Up until the first none None stack element we replace the Nones with Some(dstNode)
      val leadingNoneLength = initialCfg.markerStack.segmentLength(_.isEmpty, 0)
      initialCfg.markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ initialCfg.markerStack
        .drop(leadingNoneLength)
    }
    initialCfg.diffGraphs += diffGraph
    initialCfg
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
