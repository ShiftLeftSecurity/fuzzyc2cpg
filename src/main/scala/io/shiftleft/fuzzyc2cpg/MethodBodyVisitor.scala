package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.IfStatementBase
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, DispatchTypes, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import Utils._
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{CompoundStatement, Statement}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType

import scala.collection.JavaConverters._

class MethodBodyVisitor(originalFunctionAst: FunctionDefBase) extends ASTNodeVisitor {
  private var ast = CpgStruct.newBuilder()
  private var contextOption = Option.empty[Context]
  private var astRoot: Node = _

  private case class Context(parent: Node, childNum: Int)

  private def setContext(parent: Node, childNum: Int): Unit = {
    contextOption = Some(Context(parent, childNum))
  }

  private def context: Context = {
    contextOption.get
  }

  def convert(): (CpgStruct, Node) = {
    visit(originalFunctionAst)
    (ast.build(), astRoot)
  }

  override def visit(astFunction: FunctionDefBase): Unit = {
    astFunction.getContent.accept(this)
  }

  override def visit(node: Statement): Unit = {
    visitChildren(node)
  }

  override def visit(astAssignment: AssignmentExpression): Unit = {
    val cpgAssignment =
      Node.newBuilder()
        .setType(NodeType.CALL)
        .addStringProperty(NodePropertyName.CODE, astAssignment.getEscapedCodeStr)
        .addStringProperty(NodePropertyName.NAME, "<operator>.assignment")
        .addIntProperty(NodePropertyName.ORDER, context.childNum)
        .addIntProperty(NodePropertyName.ARGUMENT_INDEX, context.childNum)
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, "<operator>.assignment")
        .addIntProperty(NodePropertyName.LINE_NUMBER, astAssignment.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astAssignment.getLocation.startPos)
        .build

    ast.addNode(cpgAssignment)
    ast.addEdge(EdgeType.AST, cpgAssignment, context.parent)

    setContext(cpgAssignment, 1)
    astAssignment.getLeft.accept(this)
    setContext(cpgAssignment, 2)
    astAssignment.getRight.accept(this)
  }

  override def visit(astConstant: Constant): Unit = {
    val cpgConstant =
      Node.newBuilder()
        .setType(NodeType.LITERAL)
        .addStringProperty(NodePropertyName.CODE, astConstant.getEscapedCodeStr)
        .addStringProperty(NodePropertyName.NAME, astConstant.getEscapedCodeStr)
        .addIntProperty(NodePropertyName.ORDER, context.childNum)
        .addIntProperty(NodePropertyName.ARGUMENT_INDEX, context.childNum)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addIntProperty(NodePropertyName.LINE_NUMBER, astConstant.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astConstant.getLocation.startPos)
        .build

    ast.addNode(cpgConstant)
    ast.addEdge(EdgeType.AST, cpgConstant, context.parent)
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val cpgIdentifier =
      Node.newBuilder()
        .setType(NodeType.IDENTIFIER)
        .addStringProperty(NodePropertyName.CODE, astIdentifier.getEscapedCodeStr)
        .addStringProperty(NodePropertyName.NAME, astIdentifier.getEscapedCodeStr)
        .addIntProperty(NodePropertyName.ORDER, context.childNum)
        .addIntProperty(NodePropertyName.ARGUMENT_INDEX, context.childNum)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addIntProperty(NodePropertyName.LINE_NUMBER, astIdentifier.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astIdentifier.getLocation.startPos)
        .build

    ast.addNode(cpgIdentifier)
    ast.addEdge(EdgeType.AST, cpgIdentifier, context.parent)
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    val cpgBlock =
      Node.newBuilder()
        .setType(NodeType.BLOCK)
        .addIntProperty(NodePropertyName.LINE_NUMBER, astBlock.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astBlock.getLocation.startPos)
        .build

    ast.addNode(cpgBlock)
    contextOption match {
      case Some(context) =>
        ast.addEdge(EdgeType.AST, cpgBlock, context.parent)
      case None =>
        astRoot = cpgBlock
    }

    var childNum = 1
    astBlock.getStatements.asScala.foreach { statement =>
      setContext(cpgBlock, childNum)
      childNum += 1
      statement.accept(this)
    }
  }

  override def visit(astPrimaryExpression: PrimaryExpression): Unit = {
    println(astPrimaryExpression.getEscapedCodeStr)
  }

  override def visit(ifStmt: IfStatementBase): Unit = {
    //ifStmt.get
  }

  override def defaultHandler(item: AstNode): Unit = {
    throw new RuntimeException("Not implemented.")
  }

  private def addExpressionProperties(cpgNode: Node.Builder, astExpression: Expression): Unit = {

  }
}
