package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.Call
import io.shiftleft.passes.{DiffGraph, KeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.adapter.{AlwaysEdge, CfgEdgeType, EdgeProperty, FalseEdge, TrueEdge}
import io.shiftleft.fuzzyc2cpg.cfg.LayeredStack
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

class CfgCreationPass(cpg: Cpg, keyPools: Option[Iterator[KeyPool]])
    extends ParallelCpgPass[nodes.Method](cpg, keyPools = keyPools) {

  override def partIterator: Iterator[nodes.Method] = {
    cpg.method.iterator
  }

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] = {
    new CfgCreatorForMethod(method).run()
  }
}

class CfgCreatorForMethod(entryNode: nodes.Method) {

  private val logger = LoggerFactory.getLogger(getClass)

  private implicit class FringeWrapper(fringe: List[FringeElement]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      fringe.map {
        case FringeElement(node, _) =>
          FringeElement(node, cfgEdgeType)
      }
    }

    def add(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      FringeElement(node, cfgEdgeType) :: fringe
    }

    def add(ns: List[nodes.CfgNode], cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      ns.map(node => FringeElement(node, cfgEdgeType)) ++ fringe
    }

    def add(otherFringe: List[FringeElement]): List[FringeElement] = {
      otherFringe ++ fringe
    }
  }

  val diffGraph: DiffGraph.Builder = DiffGraph.newBuilder
  val exitNode: nodes.MethodReturn = entryNode.methodReturn

  private var fringe = List[FringeElement]().add(entryNode, AlwaysEdge)
  private var markerStack = List[Option[nodes.CfgNode]]() // Used to track the start of yet to be processed
  // cfg parts.
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

  private def connectGotosAndLabels(): Unit = {
    gotos.foreach {
      case (goto, label) =>
        labeledNodes.get(label) match {
          case Some(labeledNode) =>
            diffGraph.addEdge(
              goto,
              labeledNode,
              EdgeTypes.CFG
            ) // TODO
          // .addProperty(EdgeProperty.CFG_EDGE_TYPE, AlwaysEdge.toString)
          case None =>
            logger.info("Unable to wire goto statement. Missing label {}.", label)
        }
    }
  }

  private def postOrderLeftToRightExpand(node: nodes.AstNode): Unit = {
    node match {
      case n: nodes.ControlStructure =>
        handleControlStructure(n)
      case n: nodes.JumpTarget =>
        val labelName = n.name
        if (labelName.startsWith("case") || labelName.startsWith("default")) {
          pendingCaseLabels = labelName :: pendingCaseLabels
        } else {
          pendingGotoLabels = labelName :: pendingGotoLabels
        }
      case call: nodes.Call if call.name == Operators.conditional =>
        handleConditionalExpression(call)
      case call: nodes.Call if call.name == Operators.logicalAnd =>
        handleAndExpression(call)
      case call: nodes.Call if call.name == Operators.logicalOr =>
        handleOrExpression(call)
      case call: nodes.Call =>
        expandChildren(call)
        extendCfg(call)
      case identifier: nodes.Identifier =>
        extendCfg(identifier)
      case literal: nodes.Literal =>
        extendCfg(literal)
      case actualRet: nodes.Return =>
        expandChildren(actualRet)
        extendCfg(actualRet)
        fringe = Nil
        returns = actualRet :: returns
      case formalRet: nodes.MethodReturn =>
        extendCfg(formalRet)
      case n: nodes.AstNode =>
        expandChildren(n)
    }
  }

  private def connectReturnsToExit(): Unit = {
    returns.foreach { ret =>
      diffGraph.addEdge(
        ret,
        exitNode,
        EdgeTypes.CFG
      )
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
    val left = call.argument(1)
    val right = call.argument(2)
    postOrderLeftToRightExpand(left)
    val entry = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    postOrderLeftToRightExpand(right)
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

  private def handleControlStructure(node: nodes.ControlStructure): Unit = {
    node.parserTypeName match {
      case "BreakStatement" =>
        extendCfg(node)
        // Under normal conditions this is always true.
        // But if the parser missed a loop or switch statement, breakStack
        // might by empty.
        if (breakStack.numberOfLayers > 0) {
          fringe = Nil
          breakStack.store(node)
        }
      case "ContinueStatement" =>
        extendCfg(node)
        // Under normal conditions this is always true.
        // But if the parser missed a loop statement, continueStack
        // might by empty.
        if (continueStack.numberOfLayers > 0) {
          fringe = Nil
          continueStack.store(node)
        }
      case "WhileStatement" =>
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
      case "DoStatement" =>
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
      case "ForStatement" =>
      // TODO - not enough information from parser
      case "GotoStatement" =>
        extendCfg(node)
        fringe = Nil
        // TODO: the target name should be in the AST
        node.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1)).foreach { target =>
          gotos = (node, target) :: gotos
        }
      case "IfStatement" =>
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
      case "ElseStatement" =>
        expandChildren(node)
      case _ =>
    }
  }

  private def expandChildren(node: nodes.AstNode): Unit = {
    val children = node.astChildren.l
    children.foreach(postOrderLeftToRightExpand)
  }

  private def extendCfg(dstNode: nodes.CfgNode): Unit = {
    fringe.foreach {
      case FringeElement(srcNode, cfgEdgeType) =>
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
