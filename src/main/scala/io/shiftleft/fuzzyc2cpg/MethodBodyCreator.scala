package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.expressions.{AssignmentExpression, Expression}
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Condition
import io.shiftleft.fuzzyc2cpg.ast.statements.{ExpressionStatement, IdentifierDeclStatement}
import io.shiftleft.fuzzyc2cpg.cfg.{ASTToCFGConverter, CCFGFactory, CFG}
import io.shiftleft.fuzzyc2cpg.cfg.nodes._
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}
import io.shiftleft.fuzzyc2cpg.Utils.{children, newStringProperty}

import scala.collection.mutable
import scala.collection.JavaConverters._

class MethodBodyCreator() {

  def addMethodBodyCpg(methodNode : Node,
                               ast: FunctionDefBase) : CpgStruct.Builder = {

    val nodeToProtoNode: mutable.Map[CfgNode, CpgStruct.Node] =
      new mutable.HashMap[CfgNode, CpgStruct.Node]
    val bodyCpg = CpgStruct.newBuilder()
    val cfg = initializeCfg(ast)


    def addNewTrueLiteralNode(cfgNode : CfgNode): Unit = {
      val codeProperty = Node.Property.newBuilder.setName(NodePropertyName.NAME)
        .setValue(PropertyValue.newBuilder.setStringValue("<true>").build)
        .build
      val nodeBuilder = Node.newBuilder.setType(NodeType.LITERAL).addProperty(codeProperty)
      nodeToProtoNode.put(cfgNode, nodeBuilder.build)
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

    def addNodes: Unit = {
      for (cfgNode <- cfg.getVertices.asScala) {
        if (cfgNode.isInstanceOf[CfgEntryNode]) {
          // No need to add the start node. The start nodes
          // corresponds to the method node in the CPG, and
          // that's already present in the structureCpg.
          // However, we do need to add it to the `nodeToProtoNode`
          // map so that the node is present when creating edges
          nodeToProtoNode.put(cfg.getEntryNode, methodNode)
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

    def addEdges : Unit = {

    }

    addNodes
    addEdges
    bodyCpg
  }

  private def initializeCfg(ast: FunctionDefBase) : CFG = {
    val converter = new ASTToCFGConverter
    converter.setFactory(new CCFGFactory)
    converter.convert(ast)
  }

}
