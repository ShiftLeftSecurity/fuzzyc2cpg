package io.shiftleft.fuzzyc2cpg.passes.astcreation

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import org.slf4j.LoggerFactory
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, Operators, nodes}
import io.shiftleft.codepropertygraph.generated.nodes.NewNode
import io.shiftleft.fuzzyc2cpg.adapter.{EdgeKind, NodeKind, NodeProperty}
import io.shiftleft.fuzzyc2cpg.{Defines, Global}
import io.shiftleft.fuzzyc2cpg.ast.declarations.{ClassDefStatement, IdentifierDecl}
import io.shiftleft.fuzzyc2cpg.ast.expressions.{AssignmentExpression, BinaryExpression, Constant, Identifier}
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.{FunctionDef, Parameter}
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.fuzzyc2cpg.scope.Scope
import io.shiftleft.passes.DiffGraph
import io.shiftleft.proto.cpg.Cpg.{DispatchTypes, EvaluationStrategies}

import scala.jdk.CollectionConverters._

object AstCreator {}

class AstCreator(diffGraph: DiffGraph.Builder, astParentNode: nodes.NamespaceBlock, global: Global)
    extends ASTNodeVisitor {

  private val logger = LoggerFactory.getLogger(getClass)

  private val scope = new Scope[String, (nodes.CpgNode, String), nodes.CpgNode]()
  private var methodNode = Option.empty[nodes.CpgNode]
  private var methodReturnNode = Option.empty[nodes.CpgNode]

  private var contextStack = List[Context]()
  pushContext(astParentNode, 1)

  private class Context(val cpgParent: nodes.CpgNode,
                        var childNum: Int,
                        val parentIsClassDef: Boolean,
                        val parentIsMemberAccess: Boolean = false,
                        var addConditionEdgeOnNextAstEdge: Boolean = false,
                        var addArgumentEdgeOnNextAstEdge: Boolean = false) {}

  def convert(astNode: AstNode): Unit = {
    astNode.accept(this)
  }

  override def visit(astFunction: FunctionDef): Unit = {
    val returnType = if (astFunction.getReturnType != null) {
      astFunction.getReturnType.getEscapedCodeStr
    } else {
      "int"
    }
    val signature = new StringBuilder()
      .append(returnType)
      .append("(")
      .append(astFunction.getParameterList.getEscapedCodeStr(false))
      .append(")")
      .toString()

    val location = astFunction.getLocation
    val method = nodes.NewMethod(
      name = astFunction.getName,
      code = astFunction.getEscapedCodeStr,
      isExternal = false,
      fullName = astFunction.getName,
      lineNumber = location.startLine.map(x => new Integer(x)),
      columnNumber = location.startPos.map(x => new Integer(x)),
      lineNumberEnd = location.endLine.map(x => new Integer(x)),
      columnNumberEnd = location.endPos.map(x => new Integer(x)),
      signature = signature,
    )

    methodNode = Some(method)
    diffGraph.addNode(method)
    connectAstChild(method)

    pushContext(method, 1)
    scope.pushNewScope(method)

    val templateParamList = astFunction.getTemplateParameterList
    if (templateParamList != null) {
      templateParamList.asScala.foreach { template =>
        template.accept(this)
      }
    }

    val methodReturnLocation =
      if (astFunction.getReturnType != null) {
        astFunction.getReturnType.getLocation
      } else {
        astFunction.getLocation
      }

    val methodReturn = nodes.NewMethodReturn(
      code = "RET",
      evaluationStrategy = EvaluationStrategies.BY_VALUE.name(),
      typeFullName = registerType(returnType),
      lineNumber = methodReturnLocation.startLine.map(x => new Integer(x)),
      columnNumber = methodReturnLocation.startPos.map(x => new Integer(x))
    )

    methodReturnNode = Some(methodReturn)

    diffGraph.addNode(methodReturn)
    connectAstChild(methodReturn)

    astFunction.getParameterList.asScala.foreach { parameter =>
      parameter.accept(this)
    }

    astFunction.getContent.accept(this)

    scope.popScope()
    popContext()
  }

  override def visit(astParameter: Parameter): Unit = {
    val parameterType = if (astParameter.getType != null) {
      astParameter.getType.getEscapedCodeStr
    } else {
      "int"
    }
    val parameter = nodes.NewMethodParameterIn(
      code = astParameter.getEscapedCodeStr,
      name = astParameter.getName,
      order = astParameter.getChildNumber + 1,
      evaluationStrategy = EvaluationStrategies.BY_VALUE.name(),
      typeFullName = registerType(parameterType),
      lineNumber = astParameter.getLocation.startLine.map(x => new Integer(x)),
      columnNumber = astParameter.getLocation.startPos.map(x => new Integer(x))
    )
    diffGraph.addNode(parameter)
    scope.addToScope(astParameter.getName, (parameter, parameterType))
    connectAstChild(parameter)
  }

  override def visit(astIdentifierDeclStmt: IdentifierDeclStatement): Unit = {
    astIdentifierDeclStmt.getIdentifierDeclList.asScala.foreach { identifierDecl =>
      identifierDecl.accept(this)
    }
  }

  override def visit(identifierDecl: IdentifierDecl): Unit = {
    val declTypeName = identifierDecl.getType.getEscapedCodeStr

    if (identifierDecl.isTypedef) {
      val aliasTypeDecl = nodes.NewTypeDecl(
        name = identifierDecl.getName.getEscapedCodeStr,
        fullName = identifierDecl.getName.getEscapedCodeStr,
        isExternal = false,
        aliasTypeFullName = Some(registerType(declTypeName))
      )
      diffGraph.addNode(aliasTypeDecl)
      connectAstChild(aliasTypeDecl)
    } else if (context.parentIsClassDef) {
      val member =
        nodes.NewMember(
          code = identifierDecl.getEscapedCodeStr,
          name = identifierDecl.getName.getEscapedCodeStr,
          typeFullName = registerType(declTypeName)
        )
      diffGraph.addNode(member)
      connectAstChild(member)
    } else {
      // We only process file level identifier declarations if they are typedefs.
      // Everything else is ignored.
      if (!scope.isEmpty) {
        val localName = identifierDecl.getName.getEscapedCodeStr
        val local = nodes.NewLocal(
          code = localName,
          name = localName,
          typeFullName = registerType(declTypeName)
        )
        diffGraph.addNode(local)
        val scopeParentNode =
          scope.addToScope(localName, (local, declTypeName))
        // Here we on purpose do not use addAstChild because the LOCAL nodes
        // are not really in the AST (they also have no ORDER property).
        // So do not be confused that the format still demands an AST edge.
        diffGraph.addEdge(scopeParentNode, local, EdgeTypes.AST)

        val assignmentExpression = identifierDecl.getAssignment
        if (assignmentExpression != null) {
          assignmentExpression.accept(this)
        }
      }
    }
  }

  override def visit(astAssignment: AssignmentExpression): Unit = {
    val operatorMethod = astAssignment.getOperator match {
      case "="   => Operators.assignment
      case "*="  => Operators.assignmentMultiplication
      case "/="  => Operators.assignmentDivision
      case "%="  => Operators.assignmentDivision
      case "+="  => Operators.assignmentPlus
      case "-="  => Operators.assignmentMinus
      case "<<=" => Operators.assignmentShiftLeft
      case ">>=" => Operators.assignmentArithmeticShiftRight
      case "&="  => Operators.assignmentAnd
      case "^="  => Operators.assignmentXor
      case "|="  => Operators.assignmentOr
    }
    visitBinaryExpr(astAssignment, operatorMethod)
  }

  private def visitBinaryExpr(astBinaryExpr: BinaryExpression, operatorMethod: String): Unit = {
    val cpgBinaryExpr = createCallNode(astBinaryExpr, operatorMethod)
    diffGraph.addNode(cpgBinaryExpr)
    connectAstChild(cpgBinaryExpr)

    pushContext(cpgBinaryExpr, 1)

    context.addArgumentEdgeOnNextAstEdge = true
    astBinaryExpr.getLeft.accept(this)

    context.addArgumentEdgeOnNextAstEdge = true
    astBinaryExpr.getRight.accept(this)

    popContext()
  }

  override def visit(astIdentifier: Identifier): Unit = {
    val identifierName = astIdentifier.getEscapedCodeStr

    if (contextStack.nonEmpty && contextStack.head.parentIsMemberAccess && contextStack.head.childNum == 2) {
      val cpgFieldIdentifier = nodes.NewFieldIdentifier(
        canonicalName = identifierName,
        code = astIdentifier.getEscapedCodeStr,
        order = context.childNum,
        argumentIndex = context.childNum,
        lineNumber = astIdentifier.getLocation.startLine.map(x => new Integer(x)),
        columnNumber = astIdentifier.getLocation.startPos.map(x => new Integer(x))
      )
      diffGraph.addNode(cpgFieldIdentifier)
      connectAstChild(cpgFieldIdentifier)
      return
    }

    val variableOption = scope.lookupVariable(identifierName)
    val identifierTypeName = variableOption match {
      case Some((_, variableTypeName)) =>
        variableTypeName
      case None =>
        Defines.anyTypeName
    }

    val cpgIdentifier = nodes.NewIdentifier(
      name = identifierName,
      typeFullName = registerType(identifierTypeName),
      code = astIdentifier.getEscapedCodeStr,
      order = context.childNum,
      argumentIndex = context.childNum,
      lineNumber = astIdentifier.getLocation.startLine.map(x => new Integer(x)),
      columnNumber = astIdentifier.getLocation.startPos.map(x => new Integer(x))
    )
    diffGraph.addNode(cpgIdentifier)
    connectAstChild(cpgIdentifier)

    variableOption match {
      case Some((variable, _)) =>
        diffGraph.addEdge(cpgIdentifier, variable, EdgeTypes.REF)
      case None =>
    }

  }

  override def visit(astConstant: Constant): Unit = {
    val constantType = deriveConstantTypeFromCode(astConstant.getEscapedCodeStr)
    val cpgConstant = nodes.NewLiteral(
      typeFullName = registerType(constantType),
      code = astConstant.getEscapedCodeStr,
      order = context.childNum,
      argumentIndex = context.childNum,
      lineNumber = astConstant.getLocation.startLine.map(x => new Integer(x)),
      columnNumber = astConstant.getLocation.startPos.map(x => new Integer(x))
    )
    diffGraph.addNode(cpgConstant)
    connectAstChild(cpgConstant)
  }

  // TODO Implement this method properly, the current implementation is just a
  // quick hack to have some implementation at all.
  private def deriveConstantTypeFromCode(code: String): String = {
    val firstChar = code.charAt(0)
    val lastChar = code.charAt(code.length - 1)
    if (firstChar == '"') {
      Defines.charPointerTypeName
    } else if (firstChar == '\'') {
      Defines.charTypeName
    } else if (lastChar == 'f' || lastChar == 'F') {
      Defines.floatTypeName
    } else if (lastChar == 'd' || lastChar == 'D') {
      Defines.doubleTypeName
    } else if (lastChar == 'l' || lastChar == 'L') {
      Defines.longTypeName
    } else if (code.endsWith("ll") || code.endsWith("LL")) {
      Defines.longlongTypeName
    } else {
      Defines.intTypeName
    }
  }

  private def createCallNode(astNode: AstNode, methodName: String): nodes.NewCall = {
    nodes.NewCall(
      name = methodName,
      dispatchType = DispatchTypes.STATIC_DISPATCH.name(),
      signature = "TODO assignment signature",
      typeFullName = registerType(Defines.anyTypeName),
      methodFullName = methodName,
      code = astNode.getEscapedCodeStr,
      order = context.childNum,
      argumentIndex = context.childNum,
      lineNumber = astNode.getLocation.startLine.map(x => new Integer(x)),
      columnNumber = astNode.getLocation.startPos.map(x => new Integer(x))
    )
  }

  override def visit(astClassDef: ClassDefStatement): Unit = {
    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.
    var name = astClassDef.identifier.toString
    name = name.substring(1, name.length - 1)
    val baseClassList = astClassDef.baseClasses.asScala.map { identifier =>
      val baseClassName = identifier.toString
      baseClassName.substring(1, baseClassName.length - 1)
    }.toList

    baseClassList.foreach(registerType)

    val typeDecl = nodes.NewTypeDecl(
      name = name,
      fullName = name,
      isExternal = false,
      inheritsFromTypeFullName = baseClassList
    )

    diffGraph.addNode(typeDecl)
    connectAstChild(typeDecl)

    val templateParamList = astClassDef.getTemplateParameterList
    if (templateParamList != null) {
      templateParamList.asScala.foreach { template =>
        template.accept(this)
      }
    }

    pushContext(typeDecl, 1, parentIsClassDef = true)
    astClassDef.content.accept(this)
    popContext()
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    if (context.parentIsClassDef) {
      astBlock.getStatements.asScala.foreach { statement =>
        statement.accept(this)
      }
    } else {
      val block = nodes.NewBlock(
        code = "",
        order = context.childNum,
        argumentIndex = context.childNum,
        typeFullName = registerType(Defines.voidTypeName),
        lineNumber = astBlock.getLocation.startLine.map(new Integer(_)),
        columnNumber = astBlock.getLocation.startPos.map(new Integer(_))
      )
      diffGraph.addNode(block)
      connectAstChild(block)

      pushContext(block, 1)
      scope.pushNewScope(block)
      astBlock.getStatements.asScala.foreach { statement =>
        statement.accept(this)
      }
      popContext()
      scope.popScope()
    }
  }

  // Utilities

  private def registerType(typeName: String): String = {
    global.usedTypes += typeName
    typeName
  }

  private def connectAstChild(child: NewNode): Unit = {
    diffGraph.addEdge(context.cpgParent, child, EdgeTypes.AST)
    context.childNum += 1
    if (context.addConditionEdgeOnNextAstEdge) {
      diffGraph.addEdge(context.cpgParent, child, EdgeTypes.CONDITION)
      context.addConditionEdgeOnNextAstEdge = false
    }

    if (context.addArgumentEdgeOnNextAstEdge) {
      diffGraph.addEdge(context.cpgParent, child, EdgeTypes.ARGUMENT)
      context.addArgumentEdgeOnNextAstEdge = false
    }
  }

  private def context: Context = {
    contextStack.head
  }

  private def pushContext(cpgParent: nodes.CpgNode,
                          startChildNum: Int,
                          parentIsClassDef: Boolean = false,
                          parentIsMemberAccess: Boolean = false): Unit = {
    contextStack = new Context(cpgParent, startChildNum, parentIsClassDef, parentIsMemberAccess) :: contextStack
  }

  private def popContext(): Unit = {
    contextStack = contextStack.tail
  }

}
