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

  var diffGraphs: mutable.ListBuffer[DiffGraph.Builder] = mutable.ListBuffer(DiffGraph.newBuilder)

  var fringe = List[FringeElement]().add(entryNode, AlwaysEdge)
  var markerStack = List[Option[nodes.CfgNode]]()
  var labeledNodes = Map[String, nodes.CfgNode]()
  var returns = List[nodes.CfgNode]()
  val breakStack = new LayeredStack[nodes.CfgNode]()
  val continueStack = new LayeredStack[nodes.CfgNode]()
  val caseStack = new LayeredStack[nodes.CfgNode]()
  var gotos = List[(nodes.CfgNode, String)]()
}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  import CfgCreatorForMethod._

  private val logger = LoggerFactory.getLogger(getClass)
  val cfg = Cfg(entryNode)

  def run(): Iterator[DiffGraph] = {
    postOrderLeftToRightExpand(entryNode)
    connectGotosAndLabels()
    connectReturnsToExit()
    cfg.diffGraphs.map(_.build).iterator
  }

  private def postOrderLeftToRightExpand(node: nodes.AstNode): Cfg = {
    node match {
      case n: nodes.ControlStructure =>
        handleControlStructure(n)
      case n: nodes.JumpTarget =>
        handleJumpTarget(n)
      case call: nodes.Call if call.name == Operators.conditional =>
        handleConditionalExpression(call)
      case call: nodes.Call if call.name == Operators.logicalAnd =>
        handleAndExpression(call)
      case call: nodes.Call if call.name == Operators.logicalOr =>
        handleOrExpression(call)
      case call: nodes.Call =>
        handleCall(call)
      case identifier: nodes.Identifier =>
        handleIdentifier(identifier)
      case literal: nodes.Literal =>
        handleLiteral(literal)
      case actualRet: nodes.Return =>
        handleReturn(actualRet)
      case formalRet: nodes.MethodReturn =>
        handleFormalReturn(formalRet)
      case n: nodes.AstNode =>
        expandChildren(n)
    }
    cfg
  }

  private def handleCall(call: nodes.Call): Cfg = {
    expandChildren(call)
    extendCfg(call)
    cfg
  }

  private def handleIdentifier(identifier: nodes.Identifier): Cfg = {
    extendCfg(identifier)
    cfg
  }

  private def handleLiteral(literal: nodes.Literal): Cfg = {
    extendCfg(literal)
    cfg
  }

  private def handleReturn(actualRet: nodes.Return): Cfg = {
    expandChildren(actualRet)
    extendCfg(actualRet)
    cfg.fringe = Nil
    cfg.returns = actualRet :: cfg.returns
    cfg
  }

  private def handleFormalReturn(formalRet: nodes.MethodReturn): Cfg = {
    extendCfg(formalRet)
    cfg
  }

  private def connectGotosAndLabels(): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    cfg.gotos.foreach {
      case (goto, label) =>
        cfg.labeledNodes.get(label) match {
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
    cfg.diffGraphs += diffGraph
    cfg
  }

  private def connectReturnsToExit(): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    cfg.returns.foreach(
      diffGraph.addEdge(
        _,
        entryNode.methodReturn,
        EdgeTypes.CFG
      )
    )
    cfg.diffGraphs += diffGraph
    cfg
  }

  private def handleJumpTarget(n: nodes.JumpTarget): Cfg = {
    val labelName = n.name
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      // Under normal conditions this is always true.
      // But if the parser missed a switch statement, caseStack
      // might be empty.
      if (cfg.caseStack.numberOfLayers > 0) {
        cfg.caseStack.store(n)
      }
    } else {
      cfg.labeledNodes = cfg.labeledNodes + (labelName -> n)
    }
    extendCfg(n)
    cfg
  }

  private def handleConditionalExpression(call: nodes.Call): Cfg = {
    val condition = call.argument(1)
    val trueExpression = call.argument(2)
    val falseExpression = call.argument(3)

    postOrderLeftToRightExpand(condition)
    val fromCond = cfg.fringe
    cfg.fringe = cfg.fringe.setCfgEdgeType(TrueEdge)
    postOrderLeftToRightExpand(trueExpression)
    val fromTrue = cfg.fringe
    cfg.fringe = fromCond.setCfgEdgeType(FalseEdge)
    postOrderLeftToRightExpand(falseExpression)
    cfg.fringe = cfg.fringe.add(fromTrue)
    extendCfg(call)
    cfg
  }

  private def handleAndExpression(call: Call): Cfg = {
    postOrderLeftToRightExpand(call.argument(1))
    val entry = cfg.fringe
    cfg.fringe = cfg.fringe.setCfgEdgeType(TrueEdge)
    postOrderLeftToRightExpand(call.argument(2))
    cfg.fringe = cfg.fringe.add(entry.setCfgEdgeType(FalseEdge))
    extendCfg(call)
    cfg
  }

  private def handleOrExpression(call: Call): Cfg = {
    val left = call.argument(1)
    val right = call.argument(2)
    postOrderLeftToRightExpand(left)
    val entry = cfg.fringe
    cfg.fringe = cfg.fringe.setCfgEdgeType(FalseEdge)
    postOrderLeftToRightExpand(right)
    cfg.fringe = cfg.fringe.add(entry.setCfgEdgeType(TrueEdge))
    extendCfg(call)
    cfg
  }

  private def handleBreakStatement(node: nodes.ControlStructure): Cfg = {
    extendCfg(node)
    // Under normal conditions this is always true.
    // But if the parser missed a loop or switch statement, breakStack
    // might be empty.
    if (cfg.breakStack.numberOfLayers > 0) {
      cfg.fringe = Nil
      cfg.breakStack.store(node)
    }
    cfg
  }

  private def handleContinueStatement(node: nodes.ControlStructure): Cfg = {
    extendCfg(node)
    // Under normal conditions this is always true.
    // But if the parser missed a loop statement, continueStack
    // might be empty.
    if (cfg.continueStack.numberOfLayers > 0) {
      cfg.fringe = Nil
      cfg.continueStack.store(node)
    }
    cfg
  }

  private def handleWhileStatement(node: nodes.ControlStructure): Cfg = {
    cfg.breakStack.pushLayer()
    cfg.continueStack.pushLayer()

    cfg.markerStack = None :: cfg.markerStack
    node.start.condition.headOption.foreach(postOrderLeftToRightExpand)
    val conditionFringe = cfg.fringe
    cfg.fringe = cfg.fringe.setCfgEdgeType(TrueEdge)

    node.start.whenTrue.l.foreach(postOrderLeftToRightExpand)
    cfg.fringe = cfg.fringe.add(cfg.continueStack.getTopElements, AlwaysEdge)
    extendCfg(cfg.markerStack.head.get)

    cfg.fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(cfg.breakStack.getTopElements, AlwaysEdge)

    cfg.markerStack = cfg.markerStack.tail
    cfg.breakStack.popLayer()
    cfg.continueStack.popLayer()
    cfg
  }

  private def handleDoStatement(node: nodes.ControlStructure): Cfg = {
    cfg.breakStack.pushLayer()
    cfg.continueStack.pushLayer()

    cfg.markerStack = None :: cfg.markerStack
    node.astChildren.filter(_.order(1)).foreach(postOrderLeftToRightExpand)
    cfg.fringe = cfg.fringe.add(cfg.continueStack.getTopElements, AlwaysEdge)

    node.start.condition.headOption match {
      case Some(condition) =>
        postOrderLeftToRightExpand(condition)
        val conditionFringe = cfg.fringe
        cfg.fringe = cfg.fringe.setCfgEdgeType(TrueEdge)

        extendCfg(cfg.markerStack.head.get)

        cfg.fringe = conditionFringe.setCfgEdgeType(FalseEdge)
      case None =>
      // We only get here if the parser missed the condition.
      // In this case doing nothing here means that we have
      // no CFG edge to the loop start because we default
      // to an always false condition.
    }
    cfg.fringe = cfg.fringe.add(cfg.breakStack.getTopElements, AlwaysEdge)

    cfg.markerStack = cfg.markerStack.tail
    cfg.breakStack.popLayer()
    cfg.continueStack.popLayer()
    cfg
  }

  private def handleForStatement(node: nodes.ControlStructure): Cfg = {
    cfg.breakStack.pushLayer()
    cfg.continueStack.pushLayer()

    val children = node.astChildren.l
    val initExprOption = children.find(_.order == 1)
    val conditionOption = children.find(_.order == 2)
    val loopExprOption = children.find(_.order == 3)
    val statementOption = children.find(_.order == 4)

    initExprOption.foreach(postOrderLeftToRightExpand)

    cfg.markerStack = None :: cfg.markerStack
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          postOrderLeftToRightExpand(condition)
          val storedFringe = cfg.fringe
          cfg.fringe = cfg.fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None => Nil
      }

    statementOption.foreach(postOrderLeftToRightExpand)

    cfg.fringe = cfg.fringe.add(cfg.continueStack.getTopElements, AlwaysEdge)

    loopExprOption.foreach(postOrderLeftToRightExpand)

    cfg.markerStack.head.foreach(extendCfg)

    cfg.fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(cfg.breakStack.getTopElements, AlwaysEdge)

    cfg.markerStack = cfg.markerStack.tail
    cfg.breakStack.popLayer()
    cfg.continueStack.popLayer()
    cfg
  }

  private def handleGotoStatement(node: nodes.ControlStructure): Cfg = {
    extendCfg(node)
    cfg.fringe = Nil
    // TODO: the target name should be in the AST
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.foreach { target =>
      cfg.gotos = (node, target) :: cfg.gotos
    }
    cfg
  }

  private def handleIfStatement(node: nodes.ControlStructure): Cfg = {
    node.start.condition.foreach(postOrderLeftToRightExpand)
    val conditionFringe = cfg.fringe
    cfg.fringe = cfg.fringe.setCfgEdgeType(TrueEdge)
    node.start.whenTrue.foreach(postOrderLeftToRightExpand)
    node.start.whenFalse
      .map { elseStatement =>
        val ifBlockFringe = cfg.fringe
        cfg.fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        postOrderLeftToRightExpand(elseStatement)
        cfg.fringe = cfg.fringe.add(ifBlockFringe)
      }
      .headOption
      .getOrElse {
        cfg.fringe = cfg.fringe.add(conditionFringe.setCfgEdgeType(FalseEdge))
      }
    cfg
  }

  private def handleSwitchStatement(node: nodes.ControlStructure): Cfg = {
    node.start.condition.foreach(postOrderLeftToRightExpand)
    val conditionFringe = cfg.fringe.setCfgEdgeType(CaseEdge)
    cfg.fringe = Nil

    // We can only push the break and case stacks after we processed the condition
    // in order to allow for nested switches with no nodes CFG nodes in between
    // an outer switch case label and the inner switch condition.
    // This is ok because in C/C++ it is not allowed to have another switch
    // statement in the condition of a switch statement.
    cfg.breakStack.pushLayer()
    cfg.caseStack.pushLayer()

    node.start.whenTrue.foreach(postOrderLeftToRightExpand)
    val switchFringe = cfg.fringe

    cfg.caseStack.getTopElements.foreach { caseNode =>
      cfg.fringe = conditionFringe
      extendCfg(caseNode)
    }

    val hasDefaultCase = cfg.caseStack.getTopElements.exists { caseNode =>
      caseNode.asInstanceOf[nodes.JumpTarget].name == "default"
    }

    cfg.fringe = switchFringe.add(cfg.breakStack.getTopElements, AlwaysEdge)

    if (!hasDefaultCase) {
      cfg.fringe = cfg.fringe.add(conditionFringe)
    }

    cfg.breakStack.popLayer()
    cfg.caseStack.popLayer()
    cfg
  }

  private def handleControlStructure(node: nodes.ControlStructure): Cfg = {
    node.parserTypeName match {
      case "BreakStatement" =>
        handleBreakStatement(node)
      case "ContinueStatement" =>
        handleContinueStatement(node)
      case "WhileStatement" =>
        handleWhileStatement(node)
      case "DoStatement" =>
        handleDoStatement(node)
      case "ForStatement" =>
        handleForStatement(node)
      case "GotoStatement" =>
        handleGotoStatement(node)
      case "IfStatement" =>
        handleIfStatement(node)
      case "ElseStatement" =>
        expandChildren(node)
      case "SwitchStatement" =>
        handleSwitchStatement(node)
      case _ =>
    }
    cfg
  }

  private def expandChildren(node: nodes.AstNode): Cfg = {
    val children = node.astChildren.l
    children.foreach(postOrderLeftToRightExpand)
    cfg
  }

  private def extendCfg(dstNode: nodes.CfgNode): Cfg = {
    val diffGraph = DiffGraph.newBuilder
    cfg.fringe.foreach {
      case FringeElement(srcNode, _) =>
        // TODO add edge CFG edge type in CPG spec
        // val props = List(("CFG_EDGE_TYPE", cfgEdgeType.toString))
        diffGraph.addEdge(
          srcNode,
          dstNode,
          EdgeTypes.CFG
        )
    }
    cfg.fringe = Nil.add(dstNode, AlwaysEdge)

    if (cfg.markerStack.nonEmpty) {
      // Up until the first none None stack element we replace the Nones with Some(dstNode)
      val leadingNoneLength = cfg.markerStack.segmentLength(_.isEmpty, 0)
      cfg.markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ cfg.markerStack
        .drop(leadingNoneLength)
    }
    cfg.diffGraphs += diffGraph
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

    def add(otherFringe: List[FringeElement]): List[FringeElement] =
      otherFringe ++ fringe
  }

  case class FringeElement(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType)

}
