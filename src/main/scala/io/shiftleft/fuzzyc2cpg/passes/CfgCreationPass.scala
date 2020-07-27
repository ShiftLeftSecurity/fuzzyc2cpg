package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{Call, MethodReturn}
import io.shiftleft.passes.{DiffGraph, IntervalKeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.fuzzyc2cpg.passes.cfgcreation.StackOfNodeLists
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

/**
  * A pass for control flow graph generation from abstract syntax trees.
  *
  * Control flow graphs can be calculated independently for each method.
  * The pass therefore inherits from `CfgCreationPass` and returns
  * method nodes to workers of a thread pool. To ensure that ids are
  * stable across runs, a key pool is assigned to each method prior
  * to branching off into parallel computation.
  **/
class CfgCreationPass(cpg: Cpg, keyPool: IntervalKeyPool)
    extends ParallelCpgPass[nodes.Method](cpg, keyPools = Some(keyPool.split(cpg.method.size))) {

  override def partIterator: Iterator[nodes.Method] = cpg.method.iterator

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] =
    new CfgCreatorForMethod(method).run()

}

/**
  * Method control flow graph calculation from abstract syntax tree.
  *
  * We construct the CFG by traversing the AST from left to right in
  * post order, that is, we first translate leaves and then their
  * parents.
  * */
class CfgCreatorForMethod(entryNode: nodes.Method) {

  /**
    * Control flow graph definitions often feature artificial entry and exit
    * nodes which serve as single entry and exit points into the function.
    * `Return` statements are connected to the exit point via outgoing CFG edges,
    * thereby ensuring that the function has a single exit point even in the
    * presence of multiple `return` statements.
    *
    * In the Code Property Graph, instead of introducing these artificial nodes, we
    * simply reuse the METHOD node and the METHOD_RETURN node as entry and
    * exit nodes respectively. Note that the METHOD_RETURN node is the *formal*
    * return parameter (of which there exists exactly one per method) as opposed
    * to the return statements.
    * */
  private val exitNode: MethodReturn = entryNode.methodReturn

  import CfgCreatorForMethod._
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * The resulting CFG is a diffGraph, that is, it is a sequence
    * of graph modifications. In particular, in the case of CFG
    * construction, it consists entirely of edge additions.
    * */
  private val diffGraph: DiffGraph.Builder = DiffGraph.newBuilder
  private var labelToNode = Map[String, nodes.CfgNode]()
  private var gotos = List[nodes.CfgNode]()

  private var fringe: List[(nodes.CfgNode, CfgEdgeType)] = List((entryNode, AlwaysEdge))
  private var markerStack = List[Option[nodes.CfgNode]]()
  private val breakStack = new StackOfNodeLists()
  private val continueStack = new StackOfNodeLists()
  private val caseStack = new StackOfNodeLists()

  def run(): Iterator[DiffGraph] = {
    cfgForMethod(entryNode).map(_.build).iterator
  }

  /**
    * The CFG for a method is calculated in two steps. First, we handle
    * all structured control flow as well as returns, continues, and breaks,
    * and collect goto statements and labels in the process. Second, we
    * connect gotos to labels. Note that this strategy is only valid
    * because labels in C must be unique per function as opposed to
    * per scope.
    * */
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
      case call: nodes.Call =>
        handleCall(call)
      case n: nodes.JumpTarget =>
        handleJumpTarget(n)
      case actualRet: nodes.Return =>
        handleReturn(actualRet)
      case (_: nodes.Identifier | _: nodes.Literal | _: nodes.MethodReturn) =>
        handleLeaf(node.asInstanceOf[nodes.CfgNode])
      case n: nodes.AstNode =>
        cfgForChildren(n)
    }
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

  private def handleLeaf(identifier: nodes.CfgNode): Unit = {
    extendCfg(identifier)
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
    fringe = fringe ++ fromTrue
    extendCfg(call)
  }

  private def handleAndExpression(call: Call): Unit = {
    convert(call.argument(1))
    val entry = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)
    convert(call.argument(2))
    fringe = fringe ++ entry.setCfgEdgeType(FalseEdge)
    extendCfg(call)
  }

  private def handleOrExpression(call: Call): Unit = {
    val left = call.argument(1)
    val right = call.argument(2)
    convert(left)
    val entry = fringe
    fringe = fringe.setCfgEdgeType(FalseEdge)
    convert(right)
    fringe = fringe ++ entry.setCfgEdgeType(TrueEdge)
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
    breakStack.newEmptyLayer()
    continueStack.newEmptyLayer()

    markerStack = None :: markerStack
    node.start.condition.headOption.foreach(convert)
    val firstNodeOfCondition = markerStack.head.get

    val fringeAtEntry = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    node.start.whenTrue.l.foreach(convert)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)
    // At this point, continue statements and whatever was on
    // the fringe before calling `handleWhileStatement` are on
    // the fringe

    extendCfg(firstNodeOfCondition)

    // As we exit `handleWhileStatement`, we return
    // the fringe as we found it and add break statements

    fringe = fringeAtEntry
      .setCfgEdgeType(FalseEdge)
      .add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  private def handleDoStatement(node: nodes.ControlStructure): Unit = {
    breakStack.newEmptyLayer()
    continueStack.newEmptyLayer()

    markerStack = None :: markerStack
    node.astChildren.filter(_.order(1)).foreach(convert)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    node.start.condition.headOption match {
      case Some(condition) =>
        convert(condition)

        // On an empty do-block, this is actually
        // the first node of the condition
        val firstNodeInBody = markerStack.head.get

        val conditionFringe = fringe
        fringe = fringe.setCfgEdgeType(TrueEdge)

        extendCfg(firstNodeInBody)

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
    breakStack.newEmptyLayer()
    continueStack.newEmptyLayer()

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
        fringe = fringe ++ ifBlockFringe
      }
      .headOption
      .getOrElse {
        fringe = fringe ++ conditionFringe.setCfgEdgeType(FalseEdge)
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
    breakStack.newEmptyLayer()
    caseStack.newEmptyLayer()

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
      fringe = fringe ++ conditionFringe
    }

    breakStack.popLayer()
    caseStack.popLayer()
  }

  private def extendCfg(dstNode: nodes.CfgNode): Unit = {

    // TODO: we are currently not writing edge types
    // into the DiffGraph because we have not agreed
    // on the exact CFG edge types across languages

    fringe.foreach {
      case (srcNode, _) =>
        diffGraph.addEdge(
          srcNode,
          dstNode,
          EdgeTypes.CFG
        )
    }
    fringe = List((dstNode, AlwaysEdge))

    if (markerStack.nonEmpty) {
      // Up until the first element that is not `None`, replace the Nones with Some(dstNode)
      val leadingNoneLength = markerStack.segmentLength(_.isEmpty, 0)
      markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ markerStack
        .drop(leadingNoneLength)
    }
  }

}

object CfgCreatorForMethod {

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

  implicit class FringeWrapper(fringe: List[(nodes.CfgNode, CfgEdgeType)]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): List[(nodes.CfgNode, CfgEdgeType)] = {
      fringe.map {
        case (node, _) =>
          (node, cfgEdgeType)
      }
    }

    def add(ns: List[nodes.CfgNode], cfgEdgeType: CfgEdgeType): List[(nodes.CfgNode, CfgEdgeType)] =
      ns.map(node => (node, cfgEdgeType)) ++ fringe
  }

}
