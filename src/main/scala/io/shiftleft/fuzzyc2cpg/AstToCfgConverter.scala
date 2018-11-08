package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions.{BinaryExpression, Constant}
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import Utils._
import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionStatement
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.WhileStatement
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType

class AstToCfgConverter(entryNode: Node,
                        exitNode: Node,
                        astToProtoMapping: Map[AstNode, Node],
                        targetCpg: CpgStruct.Builder) extends ASTNodeVisitor {

  trait CfgEdgeType
  object TrueEdge extends CfgEdgeType
  object FalseEdge extends CfgEdgeType
  object AlwaysEdge extends CfgEdgeType

  case class FringeElement(node: Node, cfgEdgeType: CfgEdgeType)

  private def extendCfg(astDstNode: AstNode): Unit = {
    val dstNode = astToProtoMapping(astDstNode)
    extendCfg(dstNode)
  }

  private def extendCfg(dstNode: Node): Unit = {
    fringe.foreach { case FringeElement(srcNode, cfgEdgeType) =>
      targetCpg.addEdge(newEdge(EdgeType.CFG, dstNode, srcNode))
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
  private var markedCfgNode: Node = _

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
