package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.IfStatementBase
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, DispatchTypes, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import Utils._
import io.shiftleft.fuzzyc2cpg.ast.declarations.IdentifierDecl
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{CompoundStatement, Statement}
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ReturnStatement
import io.shiftleft.fuzzyc2cpg.scope.Scope
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.slf4j.{LoggerFactory, MDC}

import scala.collection.JavaConverters._

object MethodBodyVisitor {
  private val logger = LoggerFactory.getLogger(getClass)
}

class MethodBodyVisitor(originalFunctionAst: FunctionDefBase) extends ASTNodeVisitor {
  import MethodBodyVisitor._
  private var cpg: CpgStruct.Builder = _
  private var contextStack = List[Context]()
  private val scope = new Scope[String, Node, Node]()
  private var astRoot: Node = _

  private class Context(val parent: Node) {
    var childNum = 0
  }

  private def pushContext(parent: Node): Unit = {
    contextStack = new Context(parent) :: contextStack
  }

  private def popContext(): Unit = {
    contextStack = contextStack.tail
  }

  private def context: Context = {
    contextStack.head
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

    pushContext(cpgAssignment)
    context.childNum = 1
    astAssignment.getLeft.accept(this)
    context.childNum = 2
    astAssignment.getRight.accept(this)
    popContext()
  }

  override def visit(astCall: CallExpression): Unit = {
    val cpgCall = Node.newBuilder()
      .setType(NodeType.CALL)
      .addStringProperty(NodePropertyName.NAME, astCall.getTargetFunc.getEscapedCodeStr)
      // TODO the DISPATCH_TYPE needs to depend on the type of the identifier which is "called".
      // At the moment we use STATIC_DISPATCH also for calls of function pointers.
      .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
      .addStringProperty(NodePropertyName.SIGNATURE, "TODO signature")
      .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
      .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, "<operator>.assignment")
      .addCommons(astCall, context)
      .build

    cpg.addNode(cpgCall)
    cpg.addEdge(EdgeType.AST, cpgCall, context.parent)

    pushContext(cpgCall)
    var childNum = 1
    astCall.getArgumentList.iterator().asScala.foreach { argument =>
      context.childNum = childNum
      childNum += 1
      argument.accept(this)
    }
    popContext()
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
    val identifierName = astIdentifier.getEscapedCodeStr

    val cpgIdentifier =
      Node.newBuilder()
        .setType(NodeType.IDENTIFIER)
        .addStringProperty(NodePropertyName.NAME, identifierName)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astIdentifier, context)
        .build

    cpg.addNode(cpgIdentifier)
    cpg.addEdge(EdgeType.AST, cpgIdentifier, context.parent)

    scope.lookupVariable(identifierName) match {
      case Some(variable) =>
        cpg.addEdge(EdgeType.REF, variable, cpgIdentifier)
      case None =>
        MDC.put("identifier", identifierName)
        logger.warn("Cannot find variable for identifier.")
        MDC.remove("identifier")
    }
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
    contextStack match {
      case context :: _ =>
        cpg.addEdge(EdgeType.AST, cpgBlock, context.parent)
      case _ =>
        astRoot = cpgBlock
    }

    var childNum = 1
    pushContext(cpgBlock)
    scope.pushNewScope(cpgBlock)
    astBlock.getStatements.asScala.foreach { statement =>
      context.childNum = childNum
      childNum += 1
      statement.accept(this)
    }
    popContext()
    scope.popScope()
  }

  override def visit(ifStmt: IfStatementBase): Unit = {
    //ifStmt.get
  }

  override def visit(astReturnStmt: ReturnStatement): Unit = {
    val cpgReturn =
      Node.newBuilder()
      .setType(NodeType.RETURN)
      .addCommons(astReturnStmt, context)
      .build

    cpg.addNode(cpgReturn)
    cpg.addEdge(EdgeType.AST, cpgReturn, context.parent)

    pushContext(cpgReturn)
    context.childNum = 1
    astReturnStmt.getReturnExpression.accept(this)
    popContext()
  }

  override def visit(astIdentifierDeclStmt: IdentifierDeclStatement): Unit = {
    astIdentifierDeclStmt.getIdentifierDeclList.asScala.foreach { identifierDecl =>
      identifierDecl.accept(this);
    }
  }

  override def visit(identifierDecl: IdentifierDecl): Unit = {
    val localName = identifierDecl.getName.getEscapedCodeStr
    val cpgLocal = Node.newBuilder()
      .setType(NodeType.LOCAL)
      .setKey(IdPool.getNextId)
      .addStringProperty(NodePropertyName.CODE, localName)
      .addStringProperty(NodePropertyName.NAME, localName)
      .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
      .build

    val scopeParentNode = scope.addToScope(localName, cpgLocal)
    cpg.addNode(cpgLocal)
    cpg.addEdge(EdgeType.AST, cpgLocal, scopeParentNode)

    val assignmentExpression = identifierDecl.getAssignment
    if (assignmentExpression != null) {
      assignmentExpression.accept(this)
    }
  }

  override def defaultHandler(item: AstNode): Unit = {
    throw new RuntimeException("Not implemented.")
  }

}
