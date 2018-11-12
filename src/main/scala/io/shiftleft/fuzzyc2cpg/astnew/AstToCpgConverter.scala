package io.shiftleft.fuzzyc2cpg.astnew

import io.shiftleft.codepropertygraph.generated.{EvaluationStrategies, Operators}
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.declarations.IdentifierDecl
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter
import io.shiftleft.fuzzyc2cpg.ast.langc.statements.blockstarters.IfStatement
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{BlockStarter, CompoundStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.{BreakStatement, ContinueStatement, ReturnStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionStatement, IdentifierDeclStatement}
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.fuzzyc2cpg.astnew.NodeProperty.NodeProperty
import io.shiftleft.fuzzyc2cpg.scope.Scope
import io.shiftleft.proto.cpg.Cpg.DispatchTypes
import org.slf4j.{LoggerFactory, MDC}

import scala.collection.JavaConverters._

object AstToCpgConverter {
  private val logger = LoggerFactory.getLogger(getClass)
}

class AstToCpgConverter[NodeBuilderType,NodeType]
(containingFileName: String,
 adapter: CpgAdapter[NodeBuilderType, NodeType]) extends ASTNodeVisitor {
  import AstToCpgConverter._

  private var contextStack = List[Context]()
  private val scope = new Scope[String, NodeType, NodeType]()
  private var astToProtoMapping = Map[AstNode, NodeType]()
  private var methodNode = Option.empty[NodeType]
  private var methodReturnNode = Option.empty[NodeType]

  private class Context(val parent: NodeType, var childNum: Int)

  private def pushContext(parent: NodeType, startChildNum: Int): Unit = {
    contextStack = new Context(parent, startChildNum) :: contextStack
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
  private implicit class NodeBuilderWrapper2(nodeBuilder: NodeBuilderType) {
    def addProperty(property: NodeProperty, value: String): NodeBuilderType = {
      adapter.addProperty(nodeBuilder, property, value)
      nodeBuilder
    }
    def addProperty(property: NodeProperty, value: Int): NodeBuilderType = {
      adapter.addProperty(nodeBuilder, property, value)
      nodeBuilder
    }
    def createNode(astNode: AstNode): NodeType = {
      val node = adapter.createNode(nodeBuilder)

      astToProtoMapping += astNode -> node

      node
    }
    def createNode(): NodeType = {
      adapter.createNode(nodeBuilder)
    }
    def addCommons(astNode: AstNode, context: Context): NodeBuilderType = {
      nodeBuilder
        .addProperty(NodeProperty.CODE, astNode.getEscapedCodeStr)
        .addProperty(NodeProperty.ORDER, context.childNum)
        .addProperty(NodeProperty.ARGUMENT_INDEX, context.childNum)
        .addProperty(NodeProperty.LINE_NUMBER, astNode.getLocation.startLine)
        .addProperty(NodeProperty.COLUMN_NUMBER, astNode.getLocation.startPos)
    }
  }

  def getAstToProtoMapping: Map[AstNode, NodeType] = {
    astToProtoMapping
  }

  def getMethodNode: Option[NodeType] = {
    methodNode
  }

  def getMethodReturnNode: Option[NodeType] = {
    methodReturnNode
  }

  def convert(astNode: AstNode): Unit = {
    astNode.accept(this)
  }

  override def visit(astFunction: FunctionDefBase): Unit = {
    val name = astFunction.getName
    val signature = astFunction.getReturnType.getEscapedCodeStr +
      astFunction.getParameterList.asScala.map(_.getType.getEscapedCodeStr).mkString("(", ",", ")")
    val cpgMethod = adapter.createNodeBuilder(NodeKind.METHOD)
      .addProperty(NodeProperty.NAME, astFunction.getName)
      .addProperty(NodeProperty.FULL_NAME, s"$containingFileName:${astFunction.getName}")
      .addProperty(NodeProperty.LINE_NUMBER, astFunction.getLocation.startLine)
      .addProperty(NodeProperty.COLUMN_NUMBER, astFunction.getLocation.startPos)
      .addProperty(NodeProperty.SIGNATURE, signature)
      .createNode(astFunction)

      /*
      .addProperty(newStringProperty(NodeProperty.AST_PARENT_TYPE, astParentNode.getType))
      .addProperty(newStringProperty(NodeProperty.AST_PARENT_FULL_NAME,
        astParentNode.getPropertyList.asScala.find(_.getName == NodeProperty.FULL_NAME)
          .get.getValue.getStringValue))
          */
    methodNode = Some(cpgMethod)

    pushContext(cpgMethod, 1)
    scope.pushNewScope(cpgMethod)

    astFunction.getParameterList.asScala.foreach { parameter =>
      parameter.accept(this)
    }

    val cpgMethodReturn = adapter.createNodeBuilder(NodeKind.METHOD_RETURN)
      .addProperty(NodeProperty.CODE, "RET")
      .addProperty(NodeProperty.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
      .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO")
      .addProperty(NodeProperty.LINE_NUMBER, astFunction.getReturnType.getLocation.startLine)
      .addProperty(NodeProperty.COLUMN_NUMBER, astFunction.getReturnType.getLocation.startPos)
      .createNode()

    methodReturnNode = Some(cpgMethodReturn)

    addAstChild(cpgMethodReturn)

    astFunction.getContent.accept(this)

    scope.popScope()
    popContext()
  }

  override def visit(astParameter: Parameter): Unit = {
    val cpgParameter = adapter.createNodeBuilder(NodeKind.METHOD_PARAMETER_IN)
      .addProperty(NodeProperty.CODE, astParameter.getEscapedCodeStr)
      .addProperty(NodeProperty.NAME, astParameter.getName)
      .addProperty(NodeProperty.ORDER, astParameter.getChildNumber + 1)
      .addProperty(NodeProperty.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
      .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO")
      .addProperty(NodeProperty.LINE_NUMBER, astParameter.getLocation.startLine)
      .addProperty(NodeProperty.COLUMN_NUMBER, astParameter.getLocation.startPos)
      .createNode(astParameter)

    scope.addToScope(astParameter.getName, cpgParameter)
    addAstChild(cpgParameter)
  }

  override def visit(argument: Argument): Unit = {
    argument.getExpression.accept(this)
  }

  override def visit(argumentList: ArgumentList): Unit = {
    argumentList.getChildIterator.asScala.foreach { argument =>
      argument.accept(this)
    }
  }

  override def visit(astAssignment: AssignmentExpression): Unit = {
    val operatorMethod = astAssignment.getOperator match {
      case "=" => Operators.assignment
      case "+=" => Operators.assignmentPlus
    }

    val cpgAssignment = adapter.createNodeBuilder(NodeKind.CALL)
        .addProperty(NodeProperty.NAME, operatorMethod)
        .addProperty(NodeProperty.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addProperty(NodeProperty.SIGNATURE, "TODO assignment signature")
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addProperty(NodeProperty.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astAssignment, context)
        .createNode(astAssignment)

    visitBinaryExpr(astAssignment, cpgAssignment)
  }

  override def visit(astAdd: AdditiveExpression): Unit = {
    val operatorMethod = astAdd.getOperator match {
      case "+" => Operators.addition
      case "-" => Operators.subtraction
    }

    val cpgAdd = adapter.createNodeBuilder(NodeKind.CALL)
        .addProperty(NodeProperty.NAME, operatorMethod)
        .addProperty(NodeProperty.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addProperty(NodeProperty.SIGNATURE, "TODO assignment signature")
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addProperty(NodeProperty.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astAdd, context)
        .createNode(astAdd)

    visitBinaryExpr(astAdd, cpgAdd)
  }

  override def visit(astMult: MultiplicativeExpression): Unit = {
    val operatorMethod = astMult.getOperator match {
      case "*" => Operators.multiplication
      case "/" => Operators.division
      case "%" => Operators.modulo
    }

    val cpgMult = adapter.createNodeBuilder(NodeKind.CALL)
        .addProperty(NodeProperty.NAME, operatorMethod)
        .addProperty(NodeProperty.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addProperty(NodeProperty.SIGNATURE, "TODO assignment signature")
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addProperty(NodeProperty.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astMult, context)
        .createNode(astMult)

    visitBinaryExpr(astMult, cpgMult)
  }

  override def visit(astRelation: RelationalExpression): Unit = {
    val operatorMethod = astRelation.getOperator match {
      case "<" => Operators.lessThan
      case ">" => Operators.greaterThan
      case "<=" => Operators.lessEqualsThan
      case ">=" => Operators.greaterEqualsThan
    }

    val cpgRelation = adapter.createNodeBuilder(NodeKind.CALL)
        .addProperty(NodeProperty.NAME, operatorMethod)
        .addProperty(NodeProperty.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addProperty(NodeProperty.SIGNATURE, "TODO assignment signature")
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addProperty(NodeProperty.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astRelation, context)
        .createNode(astRelation)

    visitBinaryExpr(astRelation, cpgRelation)
  }

  override def visit(astCall: CallExpression): Unit = {
    val cpgCall = adapter.createNodeBuilder(NodeKind.CALL)
        .addProperty(NodeProperty.NAME, astCall.getTargetFunc.getEscapedCodeStr)
        // TODO the DISPATCH_TYPE needs to depend on the type of the identifier which is "called".
        // At the moment we use STATIC_DISPATCH also for calls of function pointers.
        .addProperty(NodeProperty.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addProperty(NodeProperty.SIGNATURE, "TODO signature")
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addProperty(NodeProperty.METHOD_INST_FULL_NAME, "<operator>.assignment")
        .addCommons(astCall, context)
        .createNode(astCall)

    addAstChild(cpgCall)

    pushContext(cpgCall, 1)
    astCall.getArgumentList.accept(this)
    popContext()
  }

  override def visit(astConstant: Constant): Unit = {
    val cpgConstant = adapter.createNodeBuilder(NodeKind.LITERAL)
        .addProperty(NodeProperty.NAME, astConstant.getEscapedCodeStr)
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astConstant, context)
        .createNode(astConstant)

    addAstChild(cpgConstant)
  }

  override def visit(astBreak: BreakStatement): Unit = {
    val cpgBreak = newUnknownNode(astBreak)

    addAstChild(cpgBreak)
  }

  override def visit(astContinue: ContinueStatement): Unit = {
    val cpgContinue = newUnknownNode(astContinue)

    addAstChild(cpgContinue)
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val identifierName = astIdentifier.getEscapedCodeStr

    val cpgIdentifier = adapter.createNodeBuilder(NodeKind.IDENTIFIER)
        .addProperty(NodeProperty.NAME, identifierName)
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astIdentifier, context)
        .createNode(astIdentifier)

    addAstChild(cpgIdentifier)

    scope.lookupVariable(identifierName) match {
      case Some(variable) =>
        adapter.addEdge(EdgeKind.REF, variable, cpgIdentifier)
      case None =>
        MDC.put("identifier", identifierName)
        logger.warn("Cannot find variable for identifier.")
        MDC.remove("identifier")
    }
  }

  override def visit(condition: Condition): Unit = {
    condition.getExpression.accept(this)
  }

  override def visit(astBlockStarter: BlockStarter): Unit = {
    val cpgBlockStarter = newUnknownNode(astBlockStarter)

    addAstChild(cpgBlockStarter)

    pushContext(cpgBlockStarter, 1)
    astBlockStarter.getChildIterator.asScala.foreach { child =>
      child.accept(this)
    }
    popContext()
  }

  override def visit(astIfStmt: IfStatement): Unit = {
    val cpgIfStmt = newUnknownNode(astIfStmt)

    addAstChild(cpgIfStmt)

    pushContext(cpgIfStmt, 1)
    astIfStmt.getCondition.accept(this)
    astIfStmt.getStatement.accept(this)
    val astElseStmt = astIfStmt.getElseNode
    if (astElseStmt != null) {
      astElseStmt.accept(this)
    }
    popContext()
  }

  override def visit(statement: ExpressionStatement): Unit = {
    statement.getExpression.accept(this)
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    val cpgBlock = adapter.createNodeBuilder(NodeKind.BLOCK)
        .addProperty(NodeProperty.ORDER, context.childNum)
        .addProperty(NodeProperty.ARGUMENT_INDEX, context.childNum)
        .addProperty(NodeProperty.LINE_NUMBER, astBlock.getLocation.startLine)
        .addProperty(NodeProperty.COLUMN_NUMBER, astBlock.getLocation.startPos)
        .createNode(astBlock)

    addAstChild(cpgBlock)

    pushContext(cpgBlock, 1)
    scope.pushNewScope(cpgBlock)
    astBlock.getStatements.asScala.foreach { statement =>
      statement.accept(this)
    }
    popContext()
    scope.popScope()
  }

  override def visit(astReturn: ReturnStatement): Unit = {
    val cpgReturn = adapter.createNodeBuilder(NodeKind.RETURN)
        .addCommons(astReturn, context)
        .createNode(astReturn)

    addAstChild(cpgReturn)

    pushContext(cpgReturn, 1)
    Option(astReturn.getReturnExpression).foreach(_.accept(this))
    popContext()
  }

  override def visit(astIdentifierDeclStmt: IdentifierDeclStatement): Unit = {
    astIdentifierDeclStmt.getIdentifierDeclList.asScala.foreach { identifierDecl =>
      identifierDecl.accept(this);
    }
  }

  override def visit(identifierDecl: IdentifierDecl): Unit = {
    val localName = identifierDecl.getName.getEscapedCodeStr
    val cpgLocal = adapter.createNodeBuilder(NodeKind.LOCAL)
        .addProperty(NodeProperty.CODE, localName)
        .addProperty(NodeProperty.NAME, localName)
        .addProperty(NodeProperty.TYPE_FULL_NAME, "TODO ANY")
        .createNode(identifierDecl)

    val scopeParentNode = scope.addToScope(localName, cpgLocal)
    // Here we on purpose do not use addAstChild because the LOCAL nodes
    // are not really in the AST (they also have no ORDER property).
    // So do not be confused that the format still demands an AST edge.
    adapter.addEdge(EdgeKind.AST, cpgLocal, scopeParentNode)

    val assignmentExpression = identifierDecl.getAssignment
    if (assignmentExpression != null) {
      assignmentExpression.accept(this)
    }
  }

  private def visitBinaryExpr(astBinaryExpr: BinaryExpression, cpgBinaryExpr: NodeType): Unit = {
    addAstChild(cpgBinaryExpr)

    pushContext(cpgBinaryExpr, 1)
    astBinaryExpr.getLeft.accept(this)
    astBinaryExpr.getRight.accept(this)
    popContext()
  }

  private def addAstChild(child: NodeType): Unit = {
    adapter.addEdge(EdgeKind.AST, child, context.parent)
    context.childNum += 1
  }

  private def newUnknownNode(astNode: AstNode): NodeType = {
    adapter.createNodeBuilder(NodeKind.UNKNOWN)
      .addProperty(NodeProperty.PARSER_TYPE_NAME, astNode.getClass.getSimpleName)
      .addCommons(astNode, context)
      .createNode(astNode)
  }
}
