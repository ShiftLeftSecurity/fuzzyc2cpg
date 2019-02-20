package io.shiftleft.fuzzyc2cpg.astnew

import io.shiftleft.codepropertygraph.generated.{EvaluationStrategies, NodeTypes, Operators}
import io.shiftleft.fuzzyc2cpg.{Defines, Utils}
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.declarations.{ClassDefStatement, IdentifierDecl}
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.{CallExpression, SizeofExpression}
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter
import io.shiftleft.fuzzyc2cpg.ast.langc.statements.blockstarters.IfStatement
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{BlockStarter, CompoundStatement, Label, Statement}
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.CatchList
import io.shiftleft.fuzzyc2cpg.ast.statements.jump._
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
 cpgParent: NodeType,
 adapter: CpgAdapter[NodeBuilderType, NodeType]) extends ASTNodeVisitor {
  import AstToCpgConverter._

  private var contextStack = List[Context]()
  private val scope = new Scope[String, NodeType, NodeType]()
  private var astToProtoMapping = Map[AstNode, NodeType]()
  private var methodNode = Option.empty[NodeType]
  private var methodReturnNode = Option.empty[NodeType]

  pushContext(cpgParent, 1)

  private class Context(val cpgParent: NodeType, var childNum: Int, val parentIsClassDef: Boolean)

  private def pushContext(cpgParent: NodeType,
                          startChildNum: Int,
                          parentIsClassDef: Boolean = false): Unit = {
    contextStack = new Context(cpgParent, startChildNum, parentIsClassDef) :: contextStack
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
    def addProperty(property: NodeProperty, value: Boolean): NodeBuilderType = {
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
    val returnType = if (astFunction.getReturnType != null) {
      astFunction.getReturnType.getEscapedCodeStr
    } else {
      "int"
    }
    val signature = returnType +
      astFunction.getParameterList.asScala.map(_.getType.getEscapedCodeStr).mkString("(", ",", ")")
    val cpgMethod = adapter.createNodeBuilder(NodeKind.METHOD)
      .addProperty(NodeProperty.NAME, astFunction.getName)
      .addProperty(NodeProperty.FULL_NAME, s"${astFunction.getName}")
      .addProperty(NodeProperty.LINE_NUMBER, astFunction.getLocation.startLine)
      .addProperty(NodeProperty.COLUMN_NUMBER, astFunction.getLocation.startPos)
      .addProperty(NodeProperty.SIGNATURE, signature)
      .createNode(astFunction)

    methodNode = Some(cpgMethod)

    addAstChild(cpgMethod)

    pushContext(cpgMethod, 1)
    scope.pushNewScope(cpgMethod)

    astFunction.getParameterList.asScala.foreach { parameter =>
      parameter.accept(this)
    }

    val methodReturnLocation =
      if (astFunction.getReturnType != null) {
        astFunction.getReturnType.getLocation
      } else {
        astFunction.getLocation
      }
    val cpgMethodReturn = adapter.createNodeBuilder(NodeKind.METHOD_RETURN)
      .addProperty(NodeProperty.CODE, "RET")
      .addProperty(NodeProperty.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
      .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
      .addProperty(NodeProperty.LINE_NUMBER, methodReturnLocation.startLine)
      .addProperty(NodeProperty.COLUMN_NUMBER, methodReturnLocation.startPos)
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
      .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
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
    acceptChildren(argumentList)
  }

  override def visit(astAssignment: AssignmentExpression): Unit = {
    val operatorMethod = astAssignment.getOperator match {
      case "=" => Operators.assignment
      case "*=" => Operators.assignmentMultiplication
      case "/=" => Operators.assignmentDivision
      case "%=" => Operators.assignmentDivision
      case "+=" => Operators.assignmentPlus
      case "-=" => Operators.assignmentMinus
      case "<<=" => Operators.assignmentShiftLeft
      case ">>=" => Operators.assignmentArithmeticShiftRight
      case "&=" => Operators.assignmentAnd
      case "^=" => Operators.assignmentXor
      case "|=" => Operators.assignmentOr
    }
    visitBinaryExpr(astAssignment, operatorMethod)
  }

  override def visit(astAdd: AdditiveExpression): Unit = {
    val operatorMethod = astAdd.getOperator match {
      case "+" => Operators.addition
      case "-" => Operators.subtraction
    }

    visitBinaryExpr(astAdd, operatorMethod)
  }

  override def visit(astMult: MultiplicativeExpression): Unit = {
    val operatorMethod = astMult.getOperator match {
      case "*" => Operators.multiplication
      case "/" => Operators.division
      case "%" => Operators.modulo
    }

    visitBinaryExpr(astMult, operatorMethod)
  }

  override def visit(astRelation: RelationalExpression): Unit = {
    val operatorMethod = astRelation.getOperator match {
      case "<" => Operators.lessThan
      case ">" => Operators.greaterThan
      case "<=" => Operators.lessEqualsThan
      case ">=" => Operators.greaterEqualsThan
    }

    visitBinaryExpr(astRelation, operatorMethod)
  }

  override def visit(astShift: ShiftExpression): Unit = {
    val operatorMethod = astShift.getOperator match {
      case "<<" => Operators.shiftLeft
      case ">>" => Operators.arithmeticShiftRight
    }

    visitBinaryExpr(astShift, operatorMethod)
  }

  override def visit(astEquality: EqualityExpression): Unit = {
    val operatorMethod = astEquality.getOperator match {
      case "==" => Operators.equals
      case "!=" => Operators.notEquals
    }

    visitBinaryExpr(astEquality, operatorMethod)
  }

  override def visit(astBitAnd: BitAndExpression): Unit = {
    visitBinaryExpr(astBitAnd, Operators.and)
  }

  override def visit(astInclOr: InclusiveOrExpression): Unit = {
    visitBinaryExpr(astInclOr, Operators.or)
  }

  override def visit(astExclOr: ExclusiveOrExpression): Unit = {
    visitBinaryExpr(astExclOr, Operators.or)
  }

  override def visit(astOr: OrExpression): Unit = {
    visitBinaryExpr(astOr, Operators.logicalOr)
  }

  override def visit(astAnd: AndExpression): Unit = {
    visitBinaryExpr(astAnd, Operators.logicalAnd)
  }

  override def visit(astUnary: UnaryExpression): Unit = {
    Option(astUnary.getChild(0)) match {
      case Some(child) =>
        val operatorMethod = astUnary.getChild(0).getEscapedCodeStr match {
          case "+" => Operators.plus
          case "-" => Operators.minus
          case "*" => Operators.indirection
          case "&" => Operators.addressOf
          case "~" => Operators.not
          case "!" => Operators.logicalNot
          case "++" => Operators.preIncrement
          case "--" => Operators.preDecrement
        }

        val cpgUnary = createCallNode(astUnary, operatorMethod)

        addAstChild(cpgUnary)

        pushContext(cpgUnary, 1)
        astUnary.getChild(1).accept(this)
        popContext()
      case None =>
        // We get here for `new` expression.
        val cpgNew = newUnknownNode(astUnary)

        addAstChild(cpgNew)
    }
  }

  override def visit(astPostIncDecOp: PostIncDecOperationExpression): Unit = {
    val operatorMethod = astPostIncDecOp.getChild(1).getEscapedCodeStr match {
      case "++" => Operators.postIncrement
      case "--" => Operators.postDecrement
    }

    val cpgPostIncDecOp = createCallNode(astPostIncDecOp, operatorMethod)

    addAstChild(cpgPostIncDecOp)

    pushContext(cpgPostIncDecOp, 1)
    astPostIncDecOp.getChild(0).accept(this)
    popContext()
  }

  override def visit(astCall: CallExpression): Unit = {
    val targetMethodName = astCall.getChild(0).getEscapedCodeStr
    // TODO the DISPATCH_TYPE needs to depend on the type of the identifier which is "called".
    // At the moment we use STATIC_DISPATCH also for calls of function pointers.
    // When this is done we need to draw a RECEIVER edge for DYNAMIC_DISPATCH function pointer
    // calls to the pointer expression.
    val cpgCall = createCallNode(astCall, targetMethodName)

    addAstChild(cpgCall)

    pushContext(cpgCall, 1)
    astCall.getArgumentList.accept(this)
    popContext()
  }

  override def visit(astConstant: Constant): Unit = {
    val cpgConstant = adapter.createNodeBuilder(NodeKind.LITERAL)
        .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
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

  override def visit(astGoto: GotoStatement): Unit = {
    val cpgGoto = newUnknownNode(astGoto)

    addAstChild(cpgGoto)
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val identifierName = astIdentifier.getEscapedCodeStr

    val cpgIdentifier = adapter.createNodeBuilder(NodeKind.IDENTIFIER)
        .addProperty(NodeProperty.NAME, identifierName)
        .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
        .addCommons(astIdentifier, context)
        .createNode(astIdentifier)

    addAstChild(cpgIdentifier)

    scope.lookupVariable(identifierName) match {
      case Some(variable) =>
        adapter.addEdge(EdgeKind.REF, variable, cpgIdentifier)
      case None =>
    }
  }

  override def visit(condition: Condition): Unit = {
    condition.getExpression.accept(this)
  }

  override def visit(astConditionalExpr: ConditionalExpression): Unit = {
    val cpgConditionalExpr = createCallNode(astConditionalExpr, "<operator>.conditionalExpression")

    addAstChild(cpgConditionalExpr)

    pushContext(cpgConditionalExpr, 1)
    acceptChildren(astConditionalExpr)
    popContext()
  }

  override def visit(expression: Expression): Unit = {
    // We only end up here for expressions chained by ','.
    // Those expressions are than the children of the expression
    // given as parameter.
    val classOfExression = expression.getClass
    if (classOfExression != classOf[Expression]) {
      throw new RuntimeException(s"Only direct instances of Expressions expected " +
        s"but ${classOfExression.getSimpleName} found")
    }

    val cpgBlock = adapter.createNodeBuilder(NodeKind.BLOCK)
      .addProperty(NodeProperty.CODE, "")
      .addProperty(NodeProperty.ORDER, context.childNum)
      .addProperty(NodeProperty.ARGUMENT_INDEX, context.childNum)
      .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
      .addProperty(NodeProperty.LINE_NUMBER, expression.getLocation.startLine)
      .addProperty(NodeProperty.COLUMN_NUMBER, expression.getLocation.startPos)
      .createNode(expression)

    addAstChild(cpgBlock)

    pushContext(cpgBlock, 1)
    acceptChildren(expression)
    popContext()
  }

  override def visit(forInit: ForInit): Unit = {
    acceptChildren(forInit)
  }

  override def visit(astBlockStarter: BlockStarter): Unit = {
    val cpgBlockStarter = newUnknownNode(astBlockStarter)

    addAstChild(cpgBlockStarter)

    pushContext(cpgBlockStarter, 1)
    acceptChildren(astBlockStarter)
    popContext()
  }

  override def visit(astCatchList: CatchList): Unit = {
    val cpgCatchList = newUnknownNode(astCatchList)
    addAstChild(cpgCatchList)

    pushContext(cpgCatchList, 1)
    astCatchList.asScala.foreach { catchElement =>
      catchElement.accept(this)
    }
    popContext()
  }

  override def visit(astThrow: ThrowStatement): Unit = {
    val cpgThrow = newUnknownNode(astThrow)

    addAstChild(cpgThrow)

    pushContext(cpgThrow, 1)
    astThrow.getThrowExpression.accept(this)
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
    Option(statement.getExpression).foreach(_.accept(this))
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    if (context.parentIsClassDef) {
      astBlock.getStatements.asScala.foreach { statement =>
        statement.accept(this)
      }
    } else {
      val cpgBlock = adapter.createNodeBuilder(NodeKind.BLOCK)
        .addProperty(NodeProperty.CODE, "")
        .addProperty(NodeProperty.ORDER, context.childNum)
        .addProperty(NodeProperty.ARGUMENT_INDEX, context.childNum)
        .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
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
    if (context.parentIsClassDef) {
      val cpgMember = adapter.createNodeBuilder(NodeKind.MEMBER)
        .addProperty(NodeProperty.CODE, identifierDecl.getEscapedCodeStr)
        .addProperty(NodeProperty.NAME, identifierDecl.getName.getEscapedCodeStr)
        .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
        .createNode(identifierDecl)
      addAstChild(cpgMember)
    } else {
      val localName = identifierDecl.getName.getEscapedCodeStr
      val cpgLocal = adapter.createNodeBuilder(NodeKind.LOCAL)
        .addProperty(NodeProperty.CODE, localName)
        .addProperty(NodeProperty.NAME, localName)
        .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
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
  }

  override def visit(astSizeof: SizeofExpression): Unit = {
    val cpgSizeof = createCallNode(astSizeof, Operators.sizeOf)

    addAstChild(cpgSizeof)

    pushContext(cpgSizeof, 1)
    // Child 0 is just the keyword 'sizeof' which at this point is duplicate
    // information for us.
    astSizeof.getChild(1).accept(this)
    popContext()
  }

  override def visit(astSizeofOperand: SizeofOperand): Unit = {
    astSizeofOperand.getChildCount match {
      case 0 =>
        // Operand is a type.
        val cpgTypeRef = newUnknownNode(astSizeofOperand)
        addAstChild(cpgTypeRef)
      case 1 =>
        // Operand is an expression.
        astSizeofOperand.getChild(1).accept(this)
    }
  }

  override def visit(astLabel: Label): Unit = {
    val cpgLabel = newUnknownNode(astLabel)
    addAstChild(cpgLabel)
  }

  override def visit(astArrayIndexing: ArrayIndexing): Unit = {
    val cpgArrayIndexing = createCallNode(astArrayIndexing, Operators.computedMemberAccess)

    addAstChild(cpgArrayIndexing)

    pushContext(cpgArrayIndexing, 1)
    astArrayIndexing.getArrayExpression.accept(this)
    astArrayIndexing.getIndexExpression.accept(this)
    popContext()
  }

  override def visit(astCast: CastExpression): Unit = {
    val cpgCast = createCallNode(astCast, Operators.cast)

    addAstChild(cpgCast)

    pushContext(cpgCast, 1)
    astCast.getCastTarget.accept(this)
    astCast.getCastExpression.accept(this)
    popContext()
  }

  override def visit(astMemberAccess: MemberAccess): Unit = {
    val cpgMemberAccess = createCallNode(astMemberAccess, Operators.memberAccess)

    addAstChild(cpgMemberAccess)

    pushContext(cpgMemberAccess, 1)
    acceptChildren(astMemberAccess)
    popContext()
  }

  override def visit(astPtrMemberAccess: PtrMemberAccess): Unit = {
    val cpgPtrMemberAccess = createCallNode(astPtrMemberAccess, Operators.indirectMemberAccess)

    addAstChild(cpgPtrMemberAccess)

    pushContext(cpgPtrMemberAccess, 1)
    acceptChildren(astPtrMemberAccess)
    popContext()
  }

  override def visit(astCastTarget: CastTarget): Unit = {
    val cpgCastTarget = newUnknownNode(astCastTarget)
    addAstChild(cpgCastTarget)
  }

  override def visit(astInitializerList: InitializerList): Unit = {
    // TODO figure out how to represent.
  }

  override def visit(statement: Statement): Unit = {
    if (statement.getChildCount != 0) {
      throw new RuntimeException("Unhandled statement type: " + statement.getClass)
    } else {
      logger.debug("Parse error. Code: {}", statement.getEscapedCodeStr)
    }
  }

  override def visit(astClassDef: ClassDefStatement): Unit = {
    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.
    var name = astClassDef.identifier.toString
    name = name.substring(1, name.length - 1)

    val cpgTypeDecl = adapter.createNodeBuilder(NodeKind.TYPE_DECL)
      .addProperty(NodeProperty.NAME, name)
      .addProperty(NodeProperty.FULL_NAME, name)
      .addProperty(NodeProperty.IS_EXTERNAL, false)
      .createNode(astClassDef)

    addAstChild(cpgTypeDecl)

    pushContext(cpgTypeDecl, 1, parentIsClassDef = true)
    astClassDef.content.accept(this)
    popContext()
  }

  private def visitBinaryExpr(astBinaryExpr: BinaryExpression, operatorMethod: String): Unit = {
    val cpgBinaryExpr = createCallNode(astBinaryExpr, operatorMethod)

    addAstChild(cpgBinaryExpr)

    pushContext(cpgBinaryExpr, 1)
    astBinaryExpr.getLeft.accept(this)
    astBinaryExpr.getRight.accept(this)
    popContext()
  }

  private def addAstChild(child: NodeType): Unit = {
    adapter.addEdge(EdgeKind.AST, child, context.cpgParent)
    context.childNum += 1
  }

  private def newUnknownNode(astNode: AstNode): NodeType = {
    adapter.createNodeBuilder(NodeKind.UNKNOWN)
      .addProperty(NodeProperty.PARSER_TYPE_NAME, astNode.getClass.getSimpleName)
      .addCommons(astNode, context)
      .createNode(astNode)
  }

  private def createCallNode(astNode: AstNode, methodName: String): NodeType = {
    val cpgNode = adapter.createNodeBuilder(NodeKind.CALL)
      .addProperty(NodeProperty.NAME, methodName)
      .addProperty(NodeProperty.DISPATCH_TYPE, DispatchTypes.STATIC_DISPATCH.name())
      .addProperty(NodeProperty.SIGNATURE, "TODO assignment signature")
      .addProperty(NodeProperty.TYPE_FULL_NAME, Defines.anyTypeName)
      .addProperty(NodeProperty.METHOD_INST_FULL_NAME, methodName)
      .addCommons(astNode, context)
      .createNode(astNode)

    adapter.createNodeBuilder(NodeKind.METHOD_INST)
      .addProperty(NodeProperty.NAME, methodName)
      .addProperty(NodeProperty.FULL_NAME, methodName)
      .addProperty(NodeProperty.SIGNATURE, "TODO assignment signature")
      .addProperty(NodeProperty.METHOD_FULL_NAME, methodName)
      .createNode()

    cpgNode
  }
}
