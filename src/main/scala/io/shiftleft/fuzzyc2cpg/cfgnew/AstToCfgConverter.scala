package io.shiftleft.fuzzyc2cpg.cfgnew

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{CompoundStatement, Label}
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionHolder, ExpressionStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.{DoStatement, ForStatement, SwitchStatement, WhileStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.{BreakStatement, ContinueStatement, GotoStatement}
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object AstToCfgConverter {
  private val logger = LoggerFactory.getLogger(getClass)
}

class AstToCfgConverter[NodeType](entryNode: NodeType,
                                  exitNode: NodeType,
                                  adapter: DestinationGraphAdapter[NodeType] = null) extends ASTNodeVisitor {
  import AstToCfgConverter._

  case class FringeElement(node: NodeType, cfgEdgeType: CfgEdgeType)

  implicit class FringeWrapper(fringe: List[FringeElement]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      fringe.map { case FringeElement(node, _) =>
        FringeElement(node, cfgEdgeType)
      }
    }

    def empty(): List[FringeElement] = {
      List()
    }

    def add(node: NodeType, cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      FringeElement(node, cfgEdgeType) :: fringe
    }

    def add(nodes: List[NodeType], cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      nodes.map(node => FringeElement(node, cfgEdgeType)) ++ fringe
    }

    def add(otherFringe: List[FringeElement]): List[FringeElement] = {
      otherFringe ++ fringe
    }
  }

  private def extendCfg(astDstNode: AstNode): Unit = {
    val dstNode = adapter.mapNode(astDstNode)
    extendCfg(dstNode)
  }

  private def extendCfg(dstNode: NodeType): Unit = {
    fringe.foreach { case FringeElement(srcNode, cfgEdgeType) =>
      adapter.newCfgEdge(dstNode, srcNode, cfgEdgeType)
    }
    fringe = fringe.empty().add(dstNode, AlwaysEdge)

    if (markerStack.nonEmpty) {
      // Up until the first none None stack element we replace the Nones with Some(dstNode)
      val leadingNoneLength  = markerStack.prefixLength(_.isEmpty)
      markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ markerStack.drop(leadingNoneLength)
    }

    if (pendingGotoLabels.nonEmpty) {
      pendingGotoLabels.foreach { label =>
        labeledNodes = labeledNodes + (label  -> dstNode)
      }
      pendingGotoLabels = List()
    }

    // TODO at the moment we discard the case labels
    if (pendingCaseLabels.nonEmpty) {
      val containsDefaultLabel = pendingCaseLabels.contains("default")
      caseStack.store(dstNode, containsDefaultLabel)
      pendingCaseLabels = List()
    }

  }

  private var fringe = List[FringeElement]().add(entryNode, AlwaysEdge)
  private var markerStack = List[Option[NodeType]]()
  private var breakStack = new LayeredStack[NodeType]()
  private var continueStack = new LayeredStack[NodeType]()
  private var caseStack = new LayeredStack[(NodeType, Boolean)]()
  private var gotos = List[(NodeType, String)]()
  private var labeledNodes = Map[String, NodeType]()
  private var pendingGotoLabels = List[String]()
  private var pendingCaseLabels = List[String]()

  private def connectGotosAndLabels(): Unit = {
    gotos.foreach { case (goto, label) =>
      labeledNodes.get(label) match {
        case Some(labeledNode) =>
          adapter.newCfgEdge(labeledNode, goto, AlwaysEdge)
        case None =>
          logger.warn("Unable to wire goto statement. Missing label {}.", label)
      }
    }
  }

  def convert(astNode: AstNode): Unit = {
    astNode.accept(this)
    extendCfg(exitNode)
    connectGotosAndLabels()
  }

  // TODO This also handles || and && for which we do not correctly model the lazyness.
  override def visit(binaryExpression: BinaryExpression): Unit = {
    binaryExpression.getLeft.accept(this)
    binaryExpression.getRight.accept(this)
    extendCfg(binaryExpression)
  }

  override def visit(breakStatement: BreakStatement): Unit = {
    val mappedBreak = adapter.mapNode(breakStatement)
    extendCfg(mappedBreak)
    fringe = fringe.empty()
    breakStack.store(mappedBreak)
  }

  override def visit(compoundStatement: CompoundStatement): Unit = {
    compoundStatement.getStatements.asScala.foreach { statement =>
      statement.accept(this)
    }
  }

  override def visit(continueStatement: ContinueStatement): Unit = {
    val mappedContinue = adapter.mapNode(continueStatement)
    extendCfg(mappedContinue)
    fringe = fringe.empty()
    continueStack.store(mappedContinue)
  }

  override def visit(constant: Constant): Unit = {
    extendCfg(constant)
  }

  override def visit(doStatement: DoStatement): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    markerStack = None :: markerStack
    doStatement.getStatement.accept(this)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    doStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    extendCfg(markerStack.head.get)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge).add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  override def visit(expression: Expression): Unit = {
    // We only end up here for expressions chained by ','.
    // Those expressions are than the children of the expression
    // given as parameter.
    if (!expression.isInstanceOf[Expression]) {
      throw new RuntimeException("Only direct instances of Expressions expected.")
    }

    expression.getChildIterator.asScala.foreach { child =>
      child.accept(this)
    }
  }

  override def visit(expressionHolder: ExpressionHolder): Unit = {
    expressionHolder.getExpression.accept(this)
  }

  override def visit(expressionStatement: ExpressionStatement): Unit = {
    expressionStatement.getExpression.accept(this)
  }

  override def visit(forInit: ForInit): Unit = {
    forInit.getChildIterator.asScala.foreach { child =>
      child.accept(this)
    }
  }

  override def visit(forStatement: ForStatement): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    Option(forStatement.getForInitExpression).foreach(_.accept(this))

    markerStack = None :: markerStack
    val conditionOption = Option(forStatement.getCondition)
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          condition.accept(this)
          val storedFringe = fringe
          fringe = fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None =>
          fringe.empty()
      }

    forStatement.getStatement.accept(this)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    Option(forStatement.getForLoopExpression).foreach(_.accept(this))

    extendCfg(markerStack.head.get)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge).add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  override def visit(functionDef: FunctionDef): Unit = {
    functionDef.getContent.accept(this)
  }

  override def visit(gotoStatement: GotoStatement): Unit = {
    val mappedGoto = adapter.mapNode(gotoStatement)
    extendCfg(mappedGoto)
    fringe = fringe.empty()
    gotos = (mappedGoto, gotoStatement.getTargetName) :: gotos
  }

  override def visit(identifier: Identifier): Unit = {
    extendCfg(identifier)
  }

  override def visit(label: Label): Unit = {
    val labelName = label.getLabelName
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      pendingCaseLabels = labelName :: pendingCaseLabels
    } else {
      pendingGotoLabels = labelName :: pendingGotoLabels
    }
  }

  override def visit(switchStatement: SwitchStatement): Unit = {
    breakStack.pushLayer()
    caseStack.pushLayer()

    switchStatement.getCondition.accept(this)
    val conditionFringe = fringe.setCfgEdgeType(CaseEdge)
    fringe = fringe.empty()

    switchStatement.getStatement.accept(this)
    val switchFringe = fringe

    val hasDefaultCase =
      caseStack.getTopElements.exists { case (caseNode, isDefault) =>
        fringe = conditionFringe
        extendCfg(caseNode)
        isDefault
      }

    fringe = switchFringe.add(breakStack.getTopElements, AlwaysEdge)

    if (!hasDefaultCase) {
      fringe = fringe.add(conditionFringe)
    }

    breakStack.popLayer()
    caseStack.popLayer()
  }

  override def visit(whileStatement: WhileStatement): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    markerStack = None :: markerStack
    whileStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    whileStatement.getStatement.accept(this)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    extendCfg(markerStack.head.get)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge).add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }
}
