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
  private var cpg: CpgStruct.Builder = _
  private var contextOption = Option.empty[Context]
  private var astRoot: Node = _

  private case class Context(parent: Node, childNum: Int)

  private def setContext(parent: Node, childNum: Int): Unit = {
    contextOption = Some(Context(parent, childNum))
  }

  private def context: Context = {
    contextOption.get
  }

  // There is already another NodeBuilderWrapper in Utils.scala.
  // We need to choose a different name here because Scala otherwise
  // gets confused witht he implicit resolution.
  private implicit class NodeBuilderWrapper2(nodeBuilder: Node.Builder) {
    def addCommons(astNode: AstNode, context: Context): Node.Builder = {
      nodeBuilder
        .setKey(IdPool.getNextId)
        .addStringProperty(NodePropertyName.CODE, astNode.getEscapedCodeStr)
        .addIntProperty(NodePropertyName.ORDER, context.childNum)
        .addIntProperty(NodePropertyName.ARGUMENT_INDEX, context.childNum)
        .addIntProperty(NodePropertyName.LINE_NUMBER, astNode.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astNode.getLocation.startPos)
    }
  }

  def convert(targetCpg: CpgStruct.Builder): Node = {
    cpg = targetCpg
    visit(originalFunctionAst)
    astRoot
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
        .addStringProperty(NodePropertyName.NAME, "<operator>.assignment")
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, "<operator>.assignment")
        .addCommons(astAssignment, context)
        .build

    cpg.addNode(cpgAssignment)
    cpg.addEdge(EdgeType.AST, cpgAssignment, context.parent)

    setContext(cpgAssignment, 1)
    astAssignment.getLeft.accept(this)
    setContext(cpgAssignment, 2)
    astAssignment.getRight.accept(this)
  }

  override def visit(astConstant: Constant): Unit = {
    val cpgConstant =
      Node.newBuilder()
        .setType(NodeType.LITERAL)
        .addStringProperty(NodePropertyName.NAME, astConstant.getEscapedCodeStr)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astConstant, context)
        .build

    cpg.addNode(cpgConstant)
    cpg.addEdge(EdgeType.AST, cpgConstant, context.parent)
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val cpgIdentifier =
      Node.newBuilder()
        .setType(NodeType.IDENTIFIER)
        .addStringProperty(NodePropertyName.NAME, astIdentifier.getEscapedCodeStr)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astIdentifier, context)
        .build

    cpg.addNode(cpgIdentifier)
    cpg.addEdge(EdgeType.AST, cpgIdentifier, context.parent)
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    val cpgBlock =
      Node.newBuilder()
        .setKey(IdPool.getNextId)
        .setType(NodeType.BLOCK)
        .addIntProperty(NodePropertyName.LINE_NUMBER, astBlock.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astBlock.getLocation.startPos)
        .build

    cpg.addNode(cpgBlock)
    contextOption match {
      case Some(context) =>
        cpg.addEdge(EdgeType.AST, cpgBlock, context.parent)
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

  override def visit(ifStmt: IfStatementBase): Unit = {
    //ifStmt.get
  }

  override def defaultHandler(item: AstNode): Unit = {
    throw new RuntimeException("Not implemented.")
  }

}
