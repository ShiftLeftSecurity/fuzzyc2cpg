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

object AstToProtoConverter {
  private val logger = LoggerFactory.getLogger(getClass)
}

class AstToProtoConverter(originalFunctionAst: FunctionDefBase,
                          methodNode: Node,
                          targetCpg: CpgStruct.Builder) extends ASTNodeVisitor {
  import AstToProtoConverter._
  private var contextStack = List[Context]()
  private val scope = new Scope[String, Node, Node]()
  private var astRoot: Node = _
  private var astToProtoMapping = Map[AstNode, Node]()

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
        .addStringProperty(NodePropertyName.CODE, astNode.getEscapedCodeStr)
        .addIntProperty(NodePropertyName.ORDER, context.childNum)
        .addIntProperty(NodePropertyName.ARGUMENT_INDEX, context.childNum)
        .addIntProperty(NodePropertyName.LINE_NUMBER, astNode.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astNode.getLocation.startPos)
    }
    def buildAndUpdateMapping(astNode: AstNode): Node = {
      val node = nodeBuilder.build()
      astToProtoMapping += astNode -> node
      node
    }
  }

  def getAstToProtoMapping: Map[AstNode, Node] = {
    astToProtoMapping
  }

  def convert(): Unit = {
    visit(originalFunctionAst)
    targetCpg.addEdge(EdgeType.AST, astRoot, methodNode)
  }

  override def visit(astFunction: FunctionDefBase): Unit = {
    astFunction.getContent.accept(this)
  }

  override def visit(statement: Statement): Unit = {
    // TODO handle statements correctly
    statement.getChildIterator.asScala.foreach { child =>
      child.accept(this)
    }
  }

  override def visit(astAssignment: AssignmentExpression): Unit = {
    val cpgAssignment =
      newNode(NodeType.CALL)
        .addStringProperty(NodePropertyName.NAME, Operators.assignment)
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, Operators.assignment)
        .addCommons(astAssignment, context)
        .buildAndUpdateMapping(astAssignment)

    visitBinaryExpr(astAssignment, cpgAssignment)
  }

  override def visit(astAdd: AdditiveExpression): Unit = {
    val operatorMethod = astAdd.getOperator match {
      case "+" => Operators.addition
      case "-" => Operators.substraction
    }

    val cpgAdd =
      newNode(NodeType.CALL)
        .addStringProperty(NodePropertyName.NAME, operatorMethod)
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astAdd, context)
        .buildAndUpdateMapping(astAdd)

    visitBinaryExpr(astAdd, cpgAdd)
  }

  override def visit(astMult: MultiplicativeExpression): Unit = {
    val operatorMethod = astMult.getOperator match {
      case "*" => Operators.multiplication
      case "/" => Operators.division
      case "%" => Operators.modulo
    }

    val cpgMult =
      newNode(NodeType.CALL)
        .addStringProperty(NodePropertyName.NAME, operatorMethod)
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astMult, context)
        .buildAndUpdateMapping(astMult)

    visitBinaryExpr(astMult, cpgMult)
  }

  override def visit(astCall: CallExpression): Unit = {
    val cpgCall =
      newNode(NodeType.CALL)
        .addStringProperty(NodePropertyName.NAME, astCall.getTargetFunc.getEscapedCodeStr)
        // TODO the DISPATCH_TYPE needs to depend on the type of the identifier which is "called".
        // At the moment we use STATIC_DISPATCH also for calls of function pointers.
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, "<operator>.assignment")
        .addCommons(astCall, context)
        .buildAndUpdateMapping(astCall)

    targetCpg.addNode(cpgCall)
    targetCpg.addEdge(EdgeType.AST, cpgCall, context.parent)

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
      newNode(NodeType.LITERAL)
        .addStringProperty(NodePropertyName.NAME, astConstant.getEscapedCodeStr)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astConstant, context)
        .buildAndUpdateMapping(astConstant)

    targetCpg.addNode(cpgConstant)
    targetCpg.addEdge(EdgeType.AST, cpgConstant, context.parent)
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val identifierName = astIdentifier.getEscapedCodeStr

    val cpgIdentifier =
      newNode(NodeType.IDENTIFIER)
        .addStringProperty(NodePropertyName.NAME, identifierName)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astIdentifier, context)
        .buildAndUpdateMapping(astIdentifier)

    targetCpg.addNode(cpgIdentifier)
    targetCpg.addEdge(EdgeType.AST, cpgIdentifier, context.parent)

    scope.lookupVariable(identifierName) match {
      case Some(variable) =>
        targetCpg.addEdge(EdgeType.REF, variable, cpgIdentifier)
      case None =>
        MDC.put("identifier", identifierName)
        logger.warn("Cannot find variable for identifier.")
        MDC.remove("identifier")
    }
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    val cpgBlock =
      newNode(NodeType.BLOCK)
        .addIntProperty(NodePropertyName.LINE_NUMBER, astBlock.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astBlock.getLocation.startPos)
        .buildAndUpdateMapping(astBlock)

    targetCpg.addNode(cpgBlock)
    contextStack match {
      case context :: _ =>
        targetCpg.addEdge(EdgeType.AST, cpgBlock, context.parent)
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

  override def visit(astReturn: ReturnStatement): Unit = {
    val cpgReturn =
      newNode(NodeType.RETURN)
        .addCommons(astReturn, context)
        .buildAndUpdateMapping(astReturn)

    targetCpg.addNode(cpgReturn)
    targetCpg.addEdge(EdgeType.AST, cpgReturn, context.parent)

    pushContext(cpgReturn)
    context.childNum = 1
    astReturn.getReturnExpression.accept(this)
    popContext()
  }

  override def visit(astIdentifierDeclStmt: IdentifierDeclStatement): Unit = {
    astIdentifierDeclStmt.getIdentifierDeclList.asScala.foreach { identifierDecl =>
      identifierDecl.accept(this);
    }
  }

  override def visit(identifierDecl: IdentifierDecl): Unit = {
    val localName = identifierDecl.getName.getEscapedCodeStr
    val cpgLocal =
      newNode(NodeType.LOCAL)
        .addStringProperty(NodePropertyName.CODE, localName)
        .addStringProperty(NodePropertyName.NAME, localName)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .buildAndUpdateMapping(identifierDecl)

    val scopeParentNode = scope.addToScope(localName, cpgLocal)
    targetCpg.addNode(cpgLocal)
    targetCpg.addEdge(EdgeType.AST, cpgLocal, scopeParentNode)

    val assignmentExpression = identifierDecl.getAssignment
    if (assignmentExpression != null) {
      assignmentExpression.accept(this)
    }
  }

  private def visitBinaryExpr(astBinaryExpr: BinaryExpression, cpgBinaryExpr: Node): Unit = {
    targetCpg.addNode(cpgBinaryExpr)
    targetCpg.addEdge(EdgeType.AST, cpgBinaryExpr, context.parent)

    pushContext(cpgBinaryExpr)
    context.childNum = 1
    astBinaryExpr.getLeft.accept(this)
    context.childNum = 2
    astBinaryExpr.getRight.accept(this)
    popContext()
  }
}
