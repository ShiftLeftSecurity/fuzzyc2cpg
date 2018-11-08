package io.shiftleft.fuzzyc2cpg.cfgnew

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions.{BinaryExpression, Constant}
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionStatement
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.WhileStatement
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor

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

  override def visit(constant: Constant): Unit = {
    extendCfg(constant)
  }

  override def visit(expressionStatement: ExpressionStatement): Unit = {
    expressionStatement.getExpression.accept(this)
  }

  override def visit(functionDef: FunctionDef): Unit = {
    functionDef.getContent.accept(this)
  }

  override def visit(whileStatement: WhileStatement): Unit = {
    markNextCfgNode = true
    whileStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    whileStatement.getStatement.accept(this)
    val whileFringe =  fringe

    extendCfg(markedCfgNode)

    fringe = whileFringe ++ conditionFringe.setCfgEdgeType(FalseEdge)
  }
}
