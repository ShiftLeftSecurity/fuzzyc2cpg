package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Call
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.passes
import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.LayeredStack
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

object EdgeProperty extends Enumeration {
  type EdgeProperty = Value
  val CFG_EDGE_TYPE: passes.EdgeProperty.Value = Value
}

object EdgeKind extends Enumeration {
  type EdgeKind = Value
  val AST, CFG, REF, CONDITION, ARGUMENT = Value
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

class CfgCreationPass(cpg: Cpg, keyPool: IntervalKeyPool)
    extends ParallelCpgPass[nodes.Method](cpg, keyPools = Some(keyPool.split(cpg.method.size))) {

  override def partIterator: Iterator[nodes.Method] = cpg.method.iterator

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] =
    new CfgCreatorForMethod(method).run()

}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  private implicit class FringeWrapper(fringe: List[FringeElement]) {
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

  private val logger = LoggerFactory.getLogger(getClass)
  val diffGraph: DiffGraph.Builder = DiffGraph.newBuilder

  private var fringe = List[FringeElement]().add(entryNode, AlwaysEdge)
  private var markerStack = List[Option[nodes.CfgNode]]()
  private case class FringeElement(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType)
  private var labeledNodes = Map[String, nodes.CfgNode]()
  private var pendingGotoLabels = List[String]()
  private var pendingCaseLabels = List[String]()
  private var returns = List[nodes.CfgNode]()
  private val breakStack = new LayeredStack[nodes.CfgNode]()
  private val continueStack = new LayeredStack[nodes.CfgNode]()
  private val caseStack = new LayeredStack[(nodes.CfgNode, Boolean)]()
  private var gotos = List[(nodes.CfgNode, String)]()

  def run(): Iterator[DiffGraph] = {
    postOrderLeftToRightExpand(entryNode)
    connectGotosAndLabels()
    connectReturnsToExit()
    Iterator(diffGraph.build)
  }

  private def postOrderLeftToRightExpand(node: nodes.AstNode): Unit = {
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
  }

  private def handleCall(call: nodes.Call): Unit = {
    expandChildren(call)
    extendCfg(call)
  }

  private def handleIdentifier(identifier: nodes.Identifier): Unit = {
    extendCfg(identifier)
  }

  private def handleLiteral(literal: nodes.Literal): Unit = {
    extendCfg(literal)
  }

  private def handleReturn(actualRet: nodes.Return): Unit = {
    expandChildren(actualRet)
    extendCfg(actualRet)
    fringe = Nil
    returns = actualRet :: returns
  }

  private def handleFormalReturn(formalRet: nodes.MethodReturn): Unit = {
    extendCfg(formalRet)
  }

  private def connectGotosAndLabels(): Unit = {
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
  }

  private def connectReturnsToExit(): Unit = {
    returns.foreach(
      diffGraph.addEdge(
        _,
        entryNode.methodReturn,
        EdgeTypes.CFG
      )
    )
  }

  private def handleJumpTarget(n: nodes.JumpTarget): Unit = {
    val labelName = n.name
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      pendingCaseLabels = labelName :: pendingCaseLabels
    } else {
      pendingGotoLabels = labelName :: pendingGotoLabels
    }
  }

  private def handleConditionalExpression(call: nodes.Call): Unit = {
    val condition = call.argument(1)
    val trueExpression = call.argument(2)
    val falseExpression = call.argument(3)

    postOrderLeftToRightExpand(condition)
    val fromCond = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    postOrderLeftToRightExpand(trueExpression)
    val fromTrue = fringe
    fringe = fromCond.setCfgEdgeType(FalseEdge)
    postOrderLeftToRightExpand(falseExpression)
    fringe = fringe.add(fromTrue)
    extendCfg(call)
  }

  private def handleAndExpression(call: Call): Unit = {
    postOrderLeftToRightExpand(call.argument(1))
    val entry = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    postOrderLeftToRightExpand(call.argument(2))
    fringe = fringe.add(entry.setCfgEdgeType(FalseEdge))
    extendCfg(call)
  }

  private def handleOrExpression(call: Call): Unit = {
    val left = call.argument(1)
    val right = call.argument(2)
    postOrderLeftToRightExpand(left)
    val entry = fringe
    fringe = fringe.setCfgEdgeType(FalseEdge)
    postOrderLeftToRightExpand(right)
    fringe = fringe.add(entry.setCfgEdgeType(TrueEdge))
    extendCfg(call)
  }

  private def handleBreakStatement(node: nodes.ControlStructure): Unit = {
    extendCfg(node)
    // Under normal conditions this is always true.
    // But if the parser missed a loop or switch statement, breakStack
    // might by empty.
    if (breakStack.numberOfLayers > 0) {
      fringe = Nil
      breakStack.store(node)
    }
  }

  private def handleContinueStatement(node: nodes.ControlStructure): Unit = {
    extendCfg(node)
    // Under normal conditions this is always true.
    // But if the parser missed a loop statement, continueStack
    // might by empty.
    if (continueStack.numberOfLayers > 0) {
      fringe = Nil
      continueStack.store(node)
    }
  }

  private def handleWhileStatement(node: nodes.ControlStructure): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    markerStack = None :: markerStack
    node.start.condition.headOption.foreach(postOrderLeftToRightExpand)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    node.start.whenTrue.l.foreach(postOrderLeftToRightExpand)
    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)
    extendCfg(markerStack.head.get)

    fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  private def handleDoStatement(node: nodes.ControlStructure): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    markerStack = None :: markerStack
    node.astChildren.filter(_.order(1)).foreach(postOrderLeftToRightExpand)
    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    node.start.condition.headOption match {
      case Some(condition) =>
        postOrderLeftToRightExpand(condition)
        val conditionFringe = fringe
        fringe = fringe.setCfgEdgeType(TrueEdge)

        extendCfg(markerStack.head.get)

        fringe = conditionFringe.setCfgEdgeType(FalseEdge)
      case None =>
      // We only get here if the parser missed the condition.
      // In this case doing nothing here means that we have
      // no CFG edge to the loop start because we default
      // to an always false condition.
    }
    fringe = fringe.add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  private def handleForStatement(node: nodes.ControlStructure): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    val children = node.astChildren.l
    val initExprOption = children.find(_.order == 1)
    val conditionOption = children.find(_.order == 2)
    val loopExprOption = children.find(_.order == 3)
    val statementOption = children.find(_.order == 4)

    initExprOption.foreach(postOrderLeftToRightExpand)

    markerStack = None :: markerStack
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          postOrderLeftToRightExpand(condition)
          val storedFringe = fringe
          fringe = fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None => Nil
      }

    statementOption.foreach(postOrderLeftToRightExpand)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    loopExprOption.foreach(postOrderLeftToRightExpand)

    markerStack.head.foreach(extendCfg)

    fringe = conditionFringe
      .setCfgEdgeType(FalseEdge)
      .add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  private def handleGotoStatement(node: nodes.ControlStructure): Unit = {
    extendCfg(node)
    fringe = Nil
    // TODO: the target name should be in the AST
    val target = node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
    target.foreach { target =>
      gotos = (node, target) :: gotos
    }
  }

  private def handleIfStatement(node: nodes.ControlStructure): Unit = {
    node.start.condition.foreach(postOrderLeftToRightExpand)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    node.start.whenTrue.foreach(postOrderLeftToRightExpand)
    node.start.whenFalse
      .map { elseStatement =>
        val ifBlockFringe = fringe
        fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        postOrderLeftToRightExpand(elseStatement)
        fringe = fringe.add(ifBlockFringe)
      }
      .headOption
      .getOrElse {
        fringe = fringe.add(conditionFringe.setCfgEdgeType(FalseEdge))
      }
  }

  private def handleSwitchStatement(node: nodes.ControlStructure): Unit = {
    node.start.condition.foreach(postOrderLeftToRightExpand)
    val conditionFringe = fringe.setCfgEdgeType(CaseEdge)
    fringe = Nil

    // We can only push the break and case stacks after we processed the condition
    // in order to allow for nested switches with no nodes CFG nodes in between
    // an outer switch case label and the inner switch condition.
    // This is ok because in C/C++ it is not allowed to have another switch
    // statement in the condition of a switch statement.
    breakStack.pushLayer()
    caseStack.pushLayer()

    node.start.whenTrue.foreach(postOrderLeftToRightExpand)
    val switchFringe = fringe

    caseStack.getTopElements.foreach {
      case (caseNode, _) =>
        fringe = conditionFringe
        extendCfg(caseNode)
    }

    val hasDefaultCase = caseStack.getTopElements.exists {
      case (_, isDefault) =>
        isDefault
    }

    fringe = switchFringe.add(breakStack.getTopElements, AlwaysEdge)

    if (!hasDefaultCase) {
      fringe = fringe.add(conditionFringe)
    }

    breakStack.popLayer()
    caseStack.popLayer()
  }

  private def handleControlStructure(node: nodes.ControlStructure): Unit = {
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
  }

  private def expandChildren(node: nodes.AstNode): Unit = {
    val children = node.astChildren.l
    children.foreach(postOrderLeftToRightExpand)
  }

  private def extendCfg(dstNode: nodes.CfgNode): Unit = {
    fringe.foreach {
      case FringeElement(srcNode, _) =>
        // TODO add edge CFG edge type in CPG spec
        // val props = List(("CFG_EDGE_TYPE", cfgEdgeType.toString))
        diffGraph.addEdge(
          srcNode,
          dstNode,
          EdgeTypes.CFG
        )
    }
    fringe = Nil.add(dstNode, AlwaysEdge)

    if (markerStack.nonEmpty) {
      // Up until the first none None stack element we replace the Nones with Some(dstNode)
      val leadingNoneLength = markerStack.segmentLength(_.isEmpty, 0)
      markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ markerStack
        .drop(leadingNoneLength)
    }

    if (pendingGotoLabels.nonEmpty) {
      pendingGotoLabels.foreach { label =>
        labeledNodes = labeledNodes + (label -> dstNode)
      }
      pendingGotoLabels = List()
    }

    // TODO at the moment we discard the case labels
    if (pendingCaseLabels.nonEmpty) {
      // Under normal conditions this is always true.
      // But if the parser missed a switch statement, caseStack
      // might by empty.
      if (caseStack.numberOfLayers > 0) {
        val containsDefaultLabel = pendingCaseLabels.contains("default")
        caseStack.store((dstNode, containsDefaultLabel))
      }
      pendingCaseLabels = List()
    }
  }

}
