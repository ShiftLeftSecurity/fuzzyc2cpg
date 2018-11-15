package io.shiftleft.fuzzyc2cpg.cfg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.declarations.{ClassDefStatement, IdentifierDecl}
import io.shiftleft.fuzzyc2cpg.ast.expressions._
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.SizeofExpression
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.langc.statements.blockstarters.{ElseStatement, IfStatement}
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.{CompoundStatement, Label, Statement}
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionHolder, ExpressionStatement, IdentifierDeclStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.{DoStatement, ForStatement, SwitchStatement, WhileStatement}
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.{BreakStatement, ContinueStatement, GotoStatement, ReturnStatement}
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object AstToCfgConverter {
  private val logger = LoggerFactory.getLogger(getClass)
}

class AstToCfgConverter[NodeType](entryNode: NodeType,
                                  exitNode: NodeType,
                                  adapter: CfgAdapter[NodeType] = null) extends ASTNodeVisitor {
  import AstToCfgConverter._

  private case class FringeElement(node: NodeType, cfgEdgeType: CfgEdgeType)

  private implicit class FringeWrapper(fringe: List[FringeElement]) {
    def setCfgEdgeType(cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      fringe.map { case FringeElement(node, _) =>
        FringeElement(node, cfgEdgeType)
      }
    }

    def empty(): List[FringeElement] = {
      List()
    }

    def add(node: NodeType, cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      FringeElement(node, cfgEdgeType) :: fringe
    }

    def add(nodes: List[NodeType], cfgEdgeType: CfgEdgeType): List[FringeElement] = {
      nodes.map(node => FringeElement(node, cfgEdgeType)) ++ fringe
    }

    def add(otherFringe: List[FringeElement]): List[FringeElement] = {
      otherFringe ++ fringe
    }
  }

  private def extendCfg(astDstNode: AstNode): Unit = {
    val dstNode = adapter.mapNode(astDstNode)
    extendCfg(dstNode)
  }

  private def extendCfg(dstNode: NodeType): Unit = {
    fringe.foreach { case FringeElement(srcNode, cfgEdgeType) =>
      adapter.newCfgEdge(dstNode, srcNode, cfgEdgeType)
    }
    fringe = fringe.empty().add(dstNode, AlwaysEdge)

    if (markerStack.nonEmpty) {
      // Up until the first none None stack element we replace the Nones with Some(dstNode)
      val leadingNoneLength  = markerStack.prefixLength(_.isEmpty)
      markerStack = List.fill(leadingNoneLength)(Some(dstNode)) ++ markerStack.drop(leadingNoneLength)
    }

    if (pendingGotoLabels.nonEmpty) {
      pendingGotoLabels.foreach { label =>
        labeledNodes = labeledNodes + (label  -> dstNode)
      }
      pendingGotoLabels = List()
    }

    // TODO at the moment we discard the case labels
    if (pendingCaseLabels.nonEmpty) {
      // Under normal conditions this is always true.
      // But if the parser missed a switch statment, caseStack
      // might by empty.
      if (caseStack.numberOfLayers > 0) {
        val containsDefaultLabel = pendingCaseLabels.contains("default")
        caseStack.store(dstNode, containsDefaultLabel)
      }
      pendingCaseLabels = List()
    }

  }

  private var fringe = List[FringeElement]().add(entryNode, AlwaysEdge)
  private var markerStack = List[Option[NodeType]]()
  private var breakStack = new LayeredStack[NodeType]()
  private var continueStack = new LayeredStack[NodeType]()
  private var caseStack = new LayeredStack[(NodeType, Boolean)]()
  private var gotos = List[(NodeType, String)]()
  private var labeledNodes = Map[String, NodeType]()
  private var pendingGotoLabels = List[String]()
  private var pendingCaseLabels = List[String]()

  private def connectGotosAndLabels(): Unit = {
    gotos.foreach { case (goto, label) =>
      labeledNodes.get(label) match {
        case Some(labeledNode) =>
          adapter.newCfgEdge(labeledNode, goto, AlwaysEdge)
        case None =>
          logger.warn("Unable to wire goto statement. Missing label {}.", label)
      }
    }
  }

  def convert(astNode: AstNode): Unit = {
    astNode.accept(this)
    extendCfg(exitNode)
    connectGotosAndLabels()
  }

  override def visit(argument: Argument): Unit = {
    argument.getExpression.accept(this)
  }

  override def visit(argumentList: ArgumentList): Unit = {
    acceptChildren(argumentList)
  }

  override def visit(arrayIndexing: ArrayIndexing): Unit = {
    arrayIndexing.getArrayExpression.accept(this)
    arrayIndexing.getIndexExpression.accept(this)
    extendCfg(arrayIndexing)
  }

  // TODO This also handles || and && for which we do not correctly model the lazyness.
  override def visit(binaryExpression: BinaryExpression): Unit = {
    binaryExpression.getLeft.accept(this)
    binaryExpression.getRight.accept(this)
    extendCfg(binaryExpression)
  }

  override def visit(breakStatement: BreakStatement): Unit = {
    val mappedBreak = adapter.mapNode(breakStatement)
    extendCfg(mappedBreak)
    // Under normal conditions this is always true.
    // But if the parser missed a loop or switch statement, breakStack
    // might by empty.
    if (breakStack.numberOfLayers > 0) {
      fringe = fringe.empty()
      breakStack.store(mappedBreak)
    }
  }

  override def visit(castExpression: CastExpression): Unit = {
    castExpression.getCastExpression.accept(this)
    extendCfg(castExpression)
  }

  // TODO we do not handle the 'targetFunc' field of callExpression yet.
  // This leads to not correctly handling calls via function pointers.
  // Fix this once we change CALL side representation for this.
  override def visit(callExpression: CallExpressionBase): Unit = {
    callExpression.getArgumentList.accept(this)
    extendCfg(callExpression)
  }

  override def visit(classDefStatement: ClassDefStatement): Unit = {
    // Class defs are not put into the control flow in CPG format.
  }

  override def visit(compoundStatement: CompoundStatement): Unit = {
    compoundStatement.getStatements.asScala.foreach { statement =>
      statement.accept(this)
    }
  }

  override def visit(condition: Condition): Unit = {
    condition.getExpression.accept(this)
  }

  // TODO We for now intentionally represent the control flow wrong
  // in order to be able to get the data flow right, but with the
  // drawback of always assuming that both branches are executed.
  // Fix once we can represent this correctly.
  override def visit(conditionalExpression: ConditionalExpression): Unit = {
    val condition = conditionalExpression.getChild(0)
    val trueExpression = conditionalExpression.getChild(1)
    val falseExpression = conditionalExpression.getChild(2)

    condition.accept(this)
    trueExpression.accept(this)
    falseExpression.accept(this)
    extendCfg(conditionalExpression)
  }

  override def visit(continueStatement: ContinueStatement): Unit = {
    val mappedContinue = adapter.mapNode(continueStatement)
    extendCfg(mappedContinue)
    // Under normal conditions this is always true.
    // But if the parser missed a loop statement, continueStack
    // might by empty.
    if (continueStack.numberOfLayers > 0) {
      fringe = fringe.empty()
      continueStack.store(mappedContinue)
    }
  }

  override def visit(constant: Constant): Unit = {
    extendCfg(constant)
  }

  override def visit(doStatement: DoStatement): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    markerStack = None :: markerStack
    doStatement.getStatement.accept(this)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    doStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    extendCfg(markerStack.head.get)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge).add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  override def visit(elseStatement: ElseStatement): Unit = {
    acceptChildren(elseStatement)
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

    acceptChildren(expression)
  }

  override def visit(expressionStatement: ExpressionStatement): Unit = {
    Option(expressionStatement.getExpression).foreach(_.accept(this))
  }

  override def visit(forInit: ForInit): Unit = {
    acceptChildren(forInit)
  }

  override def visit(forStatement: ForStatement): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    Option(forStatement.getForInitExpression).foreach(_.accept(this))

    markerStack = None :: markerStack
    val conditionOption = Option(forStatement.getCondition)
    val conditionFringe =
      conditionOption match {
        case Some(condition) =>
          condition.accept(this)
          val storedFringe = fringe
          fringe = fringe.setCfgEdgeType(TrueEdge)
          storedFringe
        case None =>
          fringe.empty()
      }

    forStatement.getStatement.accept(this)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    Option(forStatement.getForLoopExpression).foreach(_.accept(this))

    extendCfg(markerStack.head.get)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge).add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }

  override def visit(functionDef: FunctionDef): Unit = {
    functionDef.getContent.accept(this)
  }

  override def visit(gotoStatement: GotoStatement): Unit = {
    val mappedGoto = adapter.mapNode(gotoStatement)
    extendCfg(mappedGoto)
    fringe = fringe.empty()
    gotos = (mappedGoto, gotoStatement.getTargetName) :: gotos
  }

  override def visit(identifier: Identifier): Unit = {
    extendCfg(identifier)
  }

  override def visit(identifierDecl: IdentifierDecl): Unit = {
    val assignment = identifierDecl.getAssignment
    if (assignment != null) {
      assignment.accept(this)
    }
  }

  override def visit(identifierDeclStatement: IdentifierDeclStatement): Unit = {
    identifierDeclStatement.getIdentifierDeclList.asScala.foreach { identifierDecl =>
      identifierDecl.accept(this)
    }
  }

  override def visit(ifStatment: IfStatement): Unit = {
    ifStatment.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    ifStatment.getStatement.accept(this)

    Option(ifStatment.getElseNode) match {
      case Some(elseStatement) =>
        val ifBlockFringe = fringe
        fringe = conditionFringe.setCfgEdgeType(FalseEdge)
        elseStatement.accept(this)
        fringe = fringe.add(ifBlockFringe)
      case None =>
        fringe = fringe.add(conditionFringe.setCfgEdgeType(FalseEdge))
    }
  }

  override def visit(initializerList: InitializerList): Unit = {
    // TODO figure out how to represent.
  }

  override def visit(label: Label): Unit = {
    val labelName = label.getLabelName
    if (labelName.startsWith("case") || labelName.startsWith("default")) {
      pendingCaseLabels = labelName :: pendingCaseLabels
    } else {
      pendingGotoLabels = labelName :: pendingGotoLabels
    }
  }

  override def visit(memberAccess: MemberAccess): Unit = {
    acceptChildren(memberAccess)
    extendCfg(memberAccess)
  }

  override def visit(ptrMemberAccess: PtrMemberAccess): Unit = {
    acceptChildren(ptrMemberAccess)
    extendCfg(ptrMemberAccess)
  }

  // TODO We here assume that the post inc/dec is executed like a normal operation
  // and not at the end of the statement.
  override def visit(postIncDecOperationExpression: PostIncDecOperationExpression): Unit = {
    postIncDecOperationExpression.getChild(0).accept(this)
    extendCfg(postIncDecOperationExpression)
  }

  override def visit(returnStatement: ReturnStatement): Unit = {
    Option(returnStatement.getReturnExpression).foreach(_.accept(this))
    extendCfg(returnStatement)
  }

  override def visit(sizeofExpression: SizeofExpression): Unit = {
    sizeofExpression.getChild(1).accept(this)
    extendCfg(sizeofExpression)
  }

  override def visit(sizeofOperand: SizeofOperand): Unit = {
    sizeofOperand.getChildCount match {
      case 0 =>
        // Operand is a type. We do not add the type to the CFG.
      case 1 =>
        // Operand is an expression.
        sizeofOperand.getChild(0).accept(this)
    }
  }

  override def visit(statement: Statement): Unit = {
    if (statement.getChildCount != 0) {
      throw new RuntimeException("Unhandled statement type: " + statement.getClass)
    }
  }

  override def visit(switchStatement: SwitchStatement): Unit = {
    breakStack.pushLayer()
    caseStack.pushLayer()

    switchStatement.getCondition.accept(this)
    val conditionFringe = fringe.setCfgEdgeType(CaseEdge)
    fringe = fringe.empty()

    switchStatement.getStatement.accept(this)
    val switchFringe = fringe

    val hasDefaultCase =
      caseStack.getTopElements.exists { case (caseNode, isDefault) =>
        fringe = conditionFringe
        extendCfg(caseNode)
        isDefault
      }

    fringe = switchFringe.add(breakStack.getTopElements, AlwaysEdge)

    if (!hasDefaultCase) {
      fringe = fringe.add(conditionFringe)
    }

    breakStack.popLayer()
    caseStack.popLayer()
  }

  override def visit(unaryExpression: UnaryExpression): Unit = {
    // Child 0 is the operator child 1 is the operand.
    unaryExpression.getChild(1).accept(this)
    extendCfg(unaryExpression)
  }

  override def visit(whileStatement: WhileStatement): Unit = {
    breakStack.pushLayer()
    continueStack.pushLayer()

    markerStack = None :: markerStack
    whileStatement.getCondition.accept(this)
    val conditionFringe = fringe
    fringe = fringe.setCfgEdgeType(TrueEdge)

    whileStatement.getStatement.accept(this)

    fringe = fringe.add(continueStack.getTopElements, AlwaysEdge)

    extendCfg(markerStack.head.get)

    fringe = conditionFringe.setCfgEdgeType(FalseEdge).add(breakStack.getTopElements, AlwaysEdge)

    markerStack = markerStack.tail
    breakStack.popLayer()
    continueStack.popLayer()
  }
}
