package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{Call, MethodReturn}
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.LayeredStack
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

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

  val exitNode: MethodReturn = entryNode.methodReturn

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
  private case class FringeElement(node: nodes.CfgNode, cfgEdgeType: CfgEdgeType)

  private var labelToNode = Map[String, nodes.CfgNode]()
  private var gotos = List[nodes.CfgNode]()

  private var markerStack = List[Option[nodes.CfgNode]]()
  private val breakStack = new LayeredStack[nodes.CfgNode]()
  private val continueStack = new LayeredStack[nodes.CfgNode]()
  private val caseStack = new LayeredStack[nodes.CfgNode]()

  def run(): Iterator[DiffGraph] = {
    cfgForMethod(entryNode).map(_.build).iterator
  }

  private def cfgForMethod(method: nodes.Method): List[DiffGraph.Builder] = {
    cfgForChildren(method)
    cfgForGotos(gotos, labelToNode)
    List(diffGraph)
  }

  private def cfgForChildren(node: nodes.AstNode): Unit = node.astChildren.foreach(convert)

  private def cfgForGotos(gotos: List[nodes.CfgNode], labelNodes: Map[String, nodes.CfgNode]): Unit = {
    gotos.foreach { goto =>
      // TODO: the target name should be in the AST
      val label = goto.code.split(" ").lastOption.map(x => x.slice(0, x.length - 1))
      labelNodes.get(label.get) match {
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

  private def convert(node: nodes.AstNode): Unit = {
    node match {
      case n: nodes.ControlStructure =>
        handleControlStructure(n)
      case n: nodes.JumpTarget =>
        handleJumpTarget(n)
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
        cfgForChildren(n)
    }
  }

  private def handleCall(call: nodes.Call): Unit = {
    call.name match {
      case Operators.conditional =>
        handleConditionalExpression(call)
      case Operators.logicalAnd =>
        handleAndExpression(call)
      case Operators.logicalOr =>
        handleOrExpression(call)
      case _ =>
        cfgForChildren(call)
        extendCfg(call)
    }
  }

  private def handleIdentifier(identifier: nodes.Identifier): Unit = {
    extendCfg(identifier)
  }

  private def handleLiteral(literal: nodes.Literal): Unit = {
    extendCfg(literal)
  }

  private def handleReturn(actualRet: nodes.Return): Unit = {
    cfgForChildren(actualRet)
    extendCfg(actualRet)
    fringe = Nil
    diffGraph.addEdge(
      actualRet,
      exitNode,
      EdgeTypes.CFG
    )
  }

  private def handleFormalReturn(formalRet: nodes.MethodReturn): Unit = {
    extendCfg(formalRet)
  }

  private def handleJumpTarget(n: nodes.JumpTarget): Unit = {
    val labelName = n.name
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      // Under normal conditions this is always true.
      // But if the parser missed a switch statement, caseStack
      // might by empty.
      if (caseStack.numberOfLayers > 0) {
        caseStack.store(n)
      }
    } else {
      labelToNode = labelToNode + (labelName -> n)
    }
    extendCfg(n)
  }

  private def handleConditionalExpression(call: nodes.Call): Unit = {
    val condition = call.argument(1)
    val trueExpression = call.argument(2)
    val falseExpression = call.argument(3)

    convert(condition)
    val fromCond = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    convert(trueExpression)
    val fromTrue = fringe
    fringe = fromCond.setCfgEdgeType(FalseEdge)
    convert(falseExpression)
    fringe = fringe.add(fromTrue)
    extendCfg(call)
  }

  private def handleAndExpression(call: Call): Unit = {
    convert(call.argument(1))
    val entry = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    convert(call.argument(2))
    fringe = fringe.add(entry.setCfgEdgeType(FalseEdge))
    extendCfg(call)
  }

  private def handleOrExpression(call: Call): Unit = {
    val left = call.argument(1)
    val right = call.argument(2)
    convert(left)
    val entry = fringe
    fringe = fringe.setCfgEdgeType(FalseEdge)
    convert(right)
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
    node.start.condition.headOption.foreach(convert)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    node.start.whenTrue.l.foreach(convert)
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
    node.astChildren.filter(_.order(1)).foreach(convert)
    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    node.start.condition.headOption match {
      case Some(condition) =>
        convert(condition)
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

    initExprOption.foreach(convert)

    markerStack = None :: markerStack
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          convert(condition)
          val storedFringe = fringe
          fringe = fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None => Nil
      }

    statementOption.foreach(convert)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    loopExprOption.foreach(convert)

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
    gotos = node :: gotos
  }

  private def handleIfStatement(node: nodes.ControlStructure): Unit = {
    node.start.condition.foreach(convert)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    node.start.whenTrue.foreach(convert)
    node.start.whenFalse
      .map { elseStatement =>
        val ifBlockFringe = fringe
        fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        convert(elseStatement)
        fringe = fringe.add(ifBlockFringe)
      }
      .headOption
      .getOrElse {
        fringe = fringe.add(conditionFringe.setCfgEdgeType(FalseEdge))
      }
  }

  private def handleSwitchStatement(node: nodes.ControlStructure): Unit = {
    node.start.condition.foreach(convert)
    val conditionFringe = fringe.setCfgEdgeType(CaseEdge)
    fringe = Nil

    // We can only push the break and case stacks after we processed the condition
    // in order to allow for nested switches with no nodes CFG nodes in between
    // an outer switch case label and the inner switch condition.
    // This is ok because in C/C++ it is not allowed to have another switch
    // statement in the condition of a switch statement.
    breakStack.pushLayer()
    caseStack.pushLayer()

    node.start.whenTrue.foreach(convert)
    val switchFringe = fringe

    caseStack.getTopElements.foreach { caseNode =>
      fringe = conditionFringe
      extendCfg(caseNode)
    }

    val hasDefaultCase = caseStack.getTopElements.exists { n =>
      n.asInstanceOf[nodes.JumpTarget].name == "default"
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
        cfgForChildren(node)
      case "SwitchStatement" =>
        handleSwitchStatement(node)
      case _ =>
    }
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
  }

}
