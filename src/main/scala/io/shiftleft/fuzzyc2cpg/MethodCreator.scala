package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.expressions.{AssignmentExpression, Expression}
import io.shiftleft.fuzzyc2cpg.ast.functionDef.{FunctionDefBase, ParameterBase, ReturnType}
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Condition
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionStatement, IdentifierDeclStatement}
import io.shiftleft.fuzzyc2cpg.cfg.{ASTToCFGConverter, CCFGFactory, CFG}
import io.shiftleft.fuzzyc2cpg.cfg.nodes._
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}
import io.shiftleft.fuzzyc2cpg.Utils.{children, newStringProperty}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType

import scala.collection.JavaConverters._

class MethodCreator(structureCpg: CpgStruct.Builder,
                    functionDef: FunctionDefBase,
                    astParentNode: Node) {
  private var nodeToProtoNode = Map[CfgNode, CpgStruct.Node]()
  private val bodyCpg = CpgStruct.newBuilder()
  private var methodNode: CpgStruct.Node = _
  val cfg = initializeCfg(functionDef)

  def addMethodCpg(): CpgStruct.Builder = {
    methodNode = addMethodStubToStructureCpg()
    addMethodBodyCpg()
  }

  def addMethodBodyCpg() : CpgStruct.Builder = {

    addBodyNodes
    addBodyEdges
    bodyCpg
  }

  def addNewTrueLiteralNode(cfgNode : CfgNode): Unit = {
    val codeProperty = Node.Property.newBuilder.setName(NodePropertyName.NAME)
      .setValue(PropertyValue.newBuilder.setStringValue("<true>").build)
      .build
    val nodeBuilder = Node.newBuilder.setType(NodeType.LITERAL).addProperty(codeProperty)
    nodeToProtoNode += (cfgNode -> nodeBuilder.build)
    bodyCpg.addNode(nodeBuilder)
  }

  def addAllNodesOfExpression (expression: Expression) {
    addNodeForExpressionRoot(expression)
    children(expression).foreach{child =>
      addAllNodesOfExpression(child.asInstanceOf[Expression])
    }
  }

  def addNodeForExpressionRoot(expression: Expression): Unit = {
    val nodeBuilder = Node.newBuilder
    if (expression.isInstanceOf[CallExpression]) {
      val callExpression = expression.asInstanceOf[CallExpression]
      val targetFunc = callExpression.getTargetFunc
      val operator = targetFunc.getEscapedCodeStr
      nodeBuilder.addProperty(newStringProperty(NodePropertyName.NAME, operator))
    }
    bodyCpg.addNode(nodeBuilder)
  }

  def addStatementNodes(cfgNode: CfgNode): Unit = {
    assert(cfgNode.isInstanceOf[ASTNodeContainer])
    val container = cfgNode.asInstanceOf[ASTNodeContainer]
    val astNode = container.getASTNode

    astNode match {
      case parameter: Parameter => {}
      case exprStmt : ExpressionStatement => {
        val expression = exprStmt.getExpression
        addAllNodesOfExpression(expression)
      }
      case condition : Condition => {
        addAllNodesOfExpression(condition.getExpression)
      }
      case stmt: IdentifierDeclStatement => {
        stmt.getIdentifierDeclList.asScala.foreach{ node =>
          children(node).filter(_.isInstanceOf[AssignmentExpression])
            .foreach { child =>
              addAllNodesOfExpression(child.asInstanceOf[Expression])
            }
        }
      }
      case _ => println("Unhandled node type: " + astNode.getClass.getSimpleName)
    }
  }

  def addBodyNodes: Unit = {
    for (cfgNode <- cfg.getVertices.asScala) {
      if (cfgNode.isInstanceOf[CfgEntryNode]) {
        // No need to add the start node. The start nodes
        // corresponds to the method node in the CPG, and
        // that's already present in the structureCpg.
        // However, we do need to add it to the `nodeToProtoNode`
        // map so that the node is present when creating edges
        nodeToProtoNode += (cfg.getEntryNode -> methodNode)
      }
      else if (cfgNode.isInstanceOf[CfgErrorNode] ||
        cfgNode.isInstanceOf[CfgExceptionNode] ||
        cfgNode.isInstanceOf[CfgExitNode] ||
        cfgNode.isInstanceOf[InfiniteForNode])
        addNewTrueLiteralNode(cfgNode)
      else if (cfgNode.isInstanceOf[ASTNodeContainer])
        addStatementNodes(cfgNode)
    }
  }

  def addBodyEdges : Unit = {

  }

  private def initializeCfg(ast: FunctionDefBase): CFG = {
    val converter = new ASTToCFGConverter
    converter.setFactory(new CCFGFactory)
    converter.convert(ast)
  }

  def addMethodStubToStructureCpg(): CpgStruct.Node = {
    val name = functionDef.getName
    val methodNode = Node.newBuilder.setKey(IdPool.getNextId)
      .setType(NodeType.METHOD)
      .addProperty(newStringProperty(NodePropertyName.NAME, name))
      .addProperty(newStringProperty(NodePropertyName.FULL_NAME, name))
      /*
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_TYPE, astParentNode.getType))
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_FULL_NAME,
        astParentNode.getPropertyList.asScala.find(_.getName == NodePropertyName.FULL_NAME)
          .get.getValue.getStringValue))
          */
      .build

    structureCpg.addNode(methodNode)
    functionDef.getParameterList.asScala.foreach{ parameter =>
      addParameterCpg(parameter)
    }

    val retNode = children(functionDef).find(_.isInstanceOf[ReturnType])
    val retType = retNode.map(_.getEscapedCodeStr).getOrElse("")

    val methodReturnNode = Node.newBuilder.setKey(IdPool.getNextId)
      .setType(NodeType.METHOD_RETURN)
      .addProperty(newStringProperty(NodePropertyName.CODE, "RET"))
      .addProperty(newStringProperty(NodePropertyName.TYPE_FULL_NAME, retType ))
      .build
    structureCpg.addNode(methodReturnNode)

    methodNode
  }

  private def addParameterCpg(parameter: ParameterBase): Unit = {
    val codeProperty = newStringProperty(NodePropertyName.CODE, parameter.getEscapedCodeStr)
    val nameProperty = newStringProperty(NodePropertyName.NAME, parameter.getName)
    val orderProperty = Property.newBuilder
      .setName(NodePropertyName.ORDER)
      .setValue(PropertyValue.newBuilder.setIntValue(parameter.getChildNumber))
      .build

    structureCpg.addNode(
      Node.newBuilder.setKey(IdPool.getNextId)
        .setType(NodeType.METHOD_PARAMETER_IN)
        .addProperty(codeProperty)
        .addProperty(nameProperty)
        .addProperty(orderProperty).build
    )

    val evalNode = Node.newBuilder
      .setType(NodeType.TYPE)
      .addProperty(Property.newBuilder.setName(NodePropertyName.NAME)
        .setValue(PropertyValue.newBuilder.setStringValue(parameter.getType.getEscapedCodeStr)))
      .addProperty(Property.newBuilder.setName(NodePropertyName.FULL_NAME)
        .setValue(PropertyValue.newBuilder.setStringValue(parameter.getType.getEscapedCodeStr)))
      .build
    structureCpg.addNode(evalNode)
  }

}
