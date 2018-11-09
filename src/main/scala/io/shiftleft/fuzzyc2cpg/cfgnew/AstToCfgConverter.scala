package io.shiftleft.fuzzyc2cpg.cfgnew

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{CompoundStatement, Label}
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionHolder, ExpressionStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.{DoStatement, ForStatement, WhileStatement}
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

  implicit class FringeWrapper(fringe: Seq[FringeElement]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): Seq[FringeElement] = {
      fringe.map { case FringeElement(node, _) =>
        FringeElement(node, cfgEdgeType)
      }
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
    fringe = Seq(FringeElement(dstNode, AlwaysEdge))

    if (markNextCfgNode) {
      markedCfgNode = dstNode
      markNextCfgNode = false
    }

    pendingLabels.foreach { label =>
      labeledNodes = labeledNodes + (label  -> dstNode)
    }

  }

  private var fringe = Seq(FringeElement(entryNode, AlwaysEdge))
  private var markNextCfgNode = false
  private var markedCfgNode: NodeType = _
  private var nonGotoJumpStack = new NonGotoJumpStack[NodeType]()
  private var gotos = List[(NodeType, String)]()
  private var labeledNodes = Map[String, NodeType]()
  private var pendingLabels = List[String]()

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
    fringe = Seq()
    nonGotoJumpStack.storeBreak(mappedBreak)
  }

  override def visit(compoundStatement: CompoundStatement): Unit = {
    compoundStatement.getStatements.asScala.foreach { statement =>
      statement.accept(this)
    }
  }

  override def visit(continueStatement: ContinueStatement): Unit = {
    val mappedContinue = adapter.mapNode(continueStatement)
    extendCfg(mappedContinue)
    fringe = Seq()
    nonGotoJumpStack.storeContinue(mappedContinue)
  }

  override def visit(constant: Constant): Unit = {
    extendCfg(constant)
  }

  override def visit(doStatement: DoStatement): Unit = {
    markNextCfgNode = true
    nonGotoJumpStack.pushLayer()
    doStatement.getStatement.accept(this)
    val breaks = nonGotoJumpStack.getTopBreaks
    val continues = nonGotoJumpStack.getTopContinues
    nonGotoJumpStack.popLayer()

    fringe = fringe ++
      continues.map(continue => FringeElement(continue, AlwaysEdge))

    doStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    extendCfg(markedCfgNode)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge) ++
      breaks.map(break => FringeElement(break, AlwaysEdge))
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
    Option(forStatement.getForInitExpression).foreach(_.accept(this))

    markNextCfgNode = true
    val conditionOption = Option(forStatement.getCondition)
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          condition.accept(this)
          val storedFringe = fringe.setCfgEdgeType(TrueEdge)
          fringe = fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None =>
          Seq()
      }

    forStatement.getStatement.accept(this)

    Option(forStatement.getForLoopExpression).foreach(_.accept(this))

    extendCfg(markedCfgNode)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge)
  }

  override def visit(functionDef: FunctionDef): Unit = {
    functionDef.getContent.accept(this)
  }

  override def visit(gotoStatement: GotoStatement): Unit = {
    val mappedGoto = adapter.mapNode(gotoStatement)
    extendCfg(mappedGoto)
    fringe = Seq()
    gotos = (mappedGoto, gotoStatement.getTargetName) :: gotos
  }

  override def visit(identifier: Identifier): Unit = {
    extendCfg(identifier)
  }

  override def visit(label: Label): Unit = {
    pendingLabels = label.getLabelName :: pendingLabels
  }

  override def visit(whileStatement: WhileStatement): Unit = {
    markNextCfgNode = true
    whileStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    nonGotoJumpStack.pushLayer()

    whileStatement.getStatement.accept(this)

    fringe = fringe ++
      nonGotoJumpStack.getTopContinues.map(continue => FringeElement(continue, AlwaysEdge))

    extendCfg(markedCfgNode)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge) ++
      nonGotoJumpStack.getTopBreaks.map(break => FringeElement(break, AlwaysEdge))

    nonGotoJumpStack.popLayer()
  }
}
