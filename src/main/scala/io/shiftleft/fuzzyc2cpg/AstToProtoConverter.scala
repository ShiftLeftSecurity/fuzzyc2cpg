package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.{IfStatementBase, WhileStatement}
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, DispatchTypes, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import Utils._
import io.shiftleft.codepropertygraph.generated.{EvaluationStrategies, Operators}
import io.shiftleft.fuzzyc2cpg.ast.declarations.IdentifierDecl
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter
import io.shiftleft.fuzzyc2cpg.ast.langc.statements.blockstarters.IfStatement
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{BlockStarter, BlockStarterWithStmtAndCnd, CompoundStatement, Statement}
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionStatement, IdentifierDeclStatement}
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
                          containingFileName: String,
                          targetCpg: CpgStruct.Builder) extends ASTNodeVisitor {
  import AstToProtoConverter._
  private var contextStack = List[Context]()
  private val scope = new Scope[String, Node, Node]()
  private var astToProtoMapping = Map[AstNode, Node]()
  private var methodNode = Option.empty[Node]
  private var methodReturnNode = Option.empty[Node]

  private class Context(val parent: Node, var childNum: Int)

  private def pushContext(parent: Node, startChildNum: Int): Unit = {
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

  def getMethodNode: Option[Node] = {
    methodNode
  }

  def getMethodReturnNode: Option[Node] = {
    methodReturnNode
  }

  def convert(): Unit = {
    visit(originalFunctionAst)
  }

  override def visit(astFunction: FunctionDefBase): Unit = {
    val name = astFunction.getName
    val signature = astFunction.getReturnType.getEscapedCodeStr +
      astFunction.getParameterList.asScala.map(_.getType.getEscapedCodeStr).mkString("(", ",", ")")
    val cpgMethod =
      newNode(NodeType.METHOD)
      .addStringProperty(NodePropertyName.NAME, astFunction.getName)
      .addStringProperty(NodePropertyName.FULL_NAME, s"$containingFileName:${astFunction.getName}")
      .addIntProperty(NodePropertyName.LINE_NUMBER, astFunction.getLocation.startLine)
      .addIntProperty(NodePropertyName.COLUMN_NUMBER, astFunction.getLocation.startPos)
      .addStringProperty(NodePropertyName.SIGNATURE, signature)
      /*
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_TYPE, astParentNode.getType))
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_FULL_NAME,
        astParentNode.getPropertyList.asScala.find(_.getName == NodePropertyName.FULL_NAME)
          .get.getValue.getStringValue))
          */
      .buildAndUpdateMapping(astFunction)
    targetCpg.addNode(cpgMethod)
    methodNode = Some(cpgMethod)

    pushContext(cpgMethod, 1)
    scope.pushNewScope(cpgMethod)

    astFunction.getParameterList.asScala.foreach { parameter =>
      parameter.accept(this)
    }

    val cpgMethodReturn =
      newNode(NodeType.METHOD_RETURN)
        .addStringProperty(NodePropertyName.CODE, "RET")
        .addStringProperty(NodePropertyName.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO" )
        .addIntProperty(NodePropertyName.LINE_NUMBER, astFunction.getReturnType.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astFunction.getReturnType.getLocation.startPos)
        .build() // Not used buildAndUpdateMapping since there is no Ast Node from which METHOD_RETRUN is derived.
    methodReturnNode = Some(cpgMethodReturn)

    addAstChild(cpgMethodReturn)

    astFunction.getContent.accept(this)

    scope.popScope()
    popContext()
  }

  override def visit(astParameter: Parameter): Unit = {
    val cpgParameter = newNode(NodeType.METHOD_PARAMETER_IN)
      .addStringProperty(NodePropertyName.CODE, astParameter.getEscapedCodeStr)
      .addStringProperty(NodePropertyName.NAME, astParameter.getName)
      .addIntProperty(NodePropertyName.ORDER, astParameter.getChildNumber + 1)
      .addStringProperty(NodePropertyName.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
      .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO")
      .addIntProperty(NodePropertyName.LINE_NUMBER, astParameter.getLocation.startLine)
      .addIntProperty(NodePropertyName.COLUMN_NUMBER, astParameter.getLocation.startPos)
      .build

    scope.addToScope(astParameter.getName, cpgParameter)
    addAstChild(cpgParameter)
  }

  override def visit(astAssignment: AssignmentExpression): Unit = {
    val operatorMethod = astAssignment.getOperator match {
      case "=" => Operators.assignment
      case "+=" => Operators.assignmentPlus
    }

    val cpgAssignment =
      newNode(NodeType.CALL)
        .addStringProperty(NodePropertyName.NAME, operatorMethod)
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astAssignment, context)
        .buildAndUpdateMapping(astAssignment)

    visitBinaryExpr(astAssignment, cpgAssignment)
  }

  override def visit(astAdd: AdditiveExpression): Unit = {
    val operatorMethod = astAdd.getOperator match {
      case "+" => Operators.addition
      case "-" => Operators.subtraction
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

  override def visit(astRelation: RelationalExpression): Unit = {
    val operatorMethod = astRelation.getOperator match {
      case "<" => Operators.lessThan
      case ">" => Operators.greaterThan
      case "<=" => Operators.lessEqualsThan
      case ">=" => Operators.greaterEqualsThan
    }

    val cpgRelation =
      newNode(NodeType.CALL)
        .addStringProperty(NodePropertyName.NAME, operatorMethod)
        .addStringProperty(NodePropertyName.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
        .addStringProperty(NodePropertyName.SIGNATURE, "TODO assignment signature")
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addStringProperty(NodePropertyName.METHOD_INST_FULL_NAME, operatorMethod)
        .addCommons(astRelation, context)
        .buildAndUpdateMapping(astRelation)

    visitBinaryExpr(astRelation, cpgRelation)
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

    addAstChild(cpgCall)

    pushContext(cpgCall, 1)
    astCall.getArgumentList.iterator().asScala.foreach { argument =>
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

    addAstChild(cpgConstant)
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val identifierName = astIdentifier.getEscapedCodeStr

    val cpgIdentifier =
      newNode(NodeType.IDENTIFIER)
        .addStringProperty(NodePropertyName.NAME, identifierName)
        .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO ANY")
        .addCommons(astIdentifier, context)
        .buildAndUpdateMapping(astIdentifier)

    addAstChild(cpgIdentifier)

    scope.lookupVariable(identifierName) match {
      case Some(variable) =>
        targetCpg.addEdge(EdgeType.REF, variable, cpgIdentifier)
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
    val cpgBlockStarter =
      newNode(NodeType.UNKNOWN)
        .addStringProperty(NodePropertyName.PARSER_TYPE_NAME, astBlockStarter.getClass.getSimpleName)
        .addCommons(astBlockStarter, context)
        .buildAndUpdateMapping(astBlockStarter)

    addAstChild(cpgBlockStarter)

    pushContext(cpgBlockStarter, 1)
    astBlockStarter.getChildIterator.asScala.foreach { child =>
      child.accept(this)
    }
    popContext()
  }

  override def visit(astIfStmt: IfStatement): Unit = {
    val cpgIfStmt =
      newNode(NodeType.UNKNOWN)
        .addStringProperty(NodePropertyName.PARSER_TYPE_NAME, astIfStmt.getClass.getSimpleName)
        .addCommons(astIfStmt, context)
        .buildAndUpdateMapping(astIfStmt)

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
    val cpgBlock =
      newNode(NodeType.BLOCK)
        .addIntProperty(NodePropertyName.ORDER, context.childNum)
        .addIntProperty(NodePropertyName.ARGUMENT_INDEX, context.childNum)
        .addIntProperty(NodePropertyName.LINE_NUMBER, astBlock.getLocation.startLine)
        .addIntProperty(NodePropertyName.COLUMN_NUMBER, astBlock.getLocation.startPos)
        .buildAndUpdateMapping(astBlock)

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
    val cpgReturn =
      newNode(NodeType.RETURN)
        .addCommons(astReturn, context)
        .buildAndUpdateMapping(astReturn)

    addAstChild(cpgReturn)

    pushContext(cpgReturn, 1)
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
    // Here we on purpose do not use addAstChild because the LOCAL nodes
    // are not really in the AST (they also have no ORDER property).
    // So do not be confused that the format still demands an AST edge.
    targetCpg.addNode(cpgLocal)
    targetCpg.addEdge(EdgeType.AST, cpgLocal, scopeParentNode)

    val assignmentExpression = identifierDecl.getAssignment
    if (assignmentExpression != null) {
      assignmentExpression.accept(this)
    }
  }

  private def visitBinaryExpr(astBinaryExpr: BinaryExpression, cpgBinaryExpr: Node): Unit = {
    addAstChild(cpgBinaryExpr)

    pushContext(cpgBinaryExpr, 1)
    astBinaryExpr.getLeft.accept(this)
    astBinaryExpr.getRight.accept(this)
    popContext()
  }

  private def addAstChild(child: Node): Unit = {
    targetCpg.addNode(child)
    targetCpg.addEdge(EdgeType.AST, child, context.parent)
    context.childNum += 1
  }
}
