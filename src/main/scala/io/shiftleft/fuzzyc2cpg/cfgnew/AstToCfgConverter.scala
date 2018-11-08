package io.shiftleft.fuzzyc2cpg.cfgnew

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionHolder, ExpressionStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.{DoStatement, ForStatement, WhileStatement}
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor

import scala.collection.JavaConverters._

class AstToCfgConverter[NodeType](entryNode: NodeType,
                                  exitNode: NodeType,
                                  adapter: DestinationGraphAdapter[NodeType] = null) extends ASTNodeVisitor {

  case class FringeElement(node: NodeType, cfgEdgeType: CfgEdgeType)

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
  }

  implicit class FringeWrapper(fringe: Seq[FringeElement]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): Seq[FringeElement] = {
      fringe.map { case FringeElement(node, _) =>
        FringeElement(node, cfgEdgeType)
      }
    }
  }

  private var fringe = Seq(FringeElement(entryNode, AlwaysEdge))
  private var markNextCfgNode = false
  private var markedCfgNode: NodeType = _

  def convert(astNode: AstNode): Unit = {
    astNode.accept(this)
    extendCfg(exitNode)
  }

  // TODO This also handles || and && for which we do not correctly model the lazyness.
  override def visit(binaryExpression: BinaryExpression): Unit = {
    binaryExpression.getLeft.accept(this)
    binaryExpression.getRight.accept(this)
    extendCfg(binaryExpression)
  }

  override def visit(compoundStatement: CompoundStatement): Unit = {
    compoundStatement.getStatements.asScala.foreach { statement =>
      statement.accept(this)
    }
  }

  override def visit(constant: Constant): Unit = {
    extendCfg(constant)
  }

  override def visit(doStatement: DoStatement): Unit = {
    markNextCfgNode = true
    doStatement.getStatement.accept(this)

    doStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    extendCfg(markedCfgNode)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge)
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

  override def visit(identifier: Identifier): Unit = {
    extendCfg(identifier)
  }

  override def visit(whileStatement: WhileStatement): Unit = {
    markNextCfgNode = true
    whileStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    whileStatement.getStatement.accept(this)

    extendCfg(markedCfgNode)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge)
  }
}
