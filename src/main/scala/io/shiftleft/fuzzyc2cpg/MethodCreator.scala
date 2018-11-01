package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.EvaluationStrategies
import io.shiftleft.fuzzyc2cpg.ast.functionDef.{FunctionDefBase, ParameterBase, ReturnType}
import io.shiftleft.fuzzyc2cpg.cfg.{CAstToCfgConverter, CFG}
import io.shiftleft.fuzzyc2cpg.cfg.nodes._
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType

import scala.collection.JavaConverters._

class MethodCreator(functionDef: FunctionDefBase,
                    astParentNode: Node,
                    containingFileName: String) {
  private val bodyCpg = CpgStruct.newBuilder()

  def addMethodCpg(): CpgStruct.Builder = {
    val (methodNode, methodExitNode) = convertMethodHeader()
    val astToProtoMapping = addMethodBodyAst(methodNode)
    addMethodBodyCfg(methodNode, methodExitNode, astToProtoMapping)

    bodyCpg
  }

  private def addMethodBodyAst(methodNode: Node): Map[AstNode, Node] = {
    val bodyVisitor = new AstToProtoConverter(functionDef, methodNode, bodyCpg)
    bodyVisitor.convert()
    bodyVisitor.getAstToProtoMapping
  }

  private def addMethodBodyCfg(methodNode: Node,
                               methodExitNode: Node,
                               astToProtoMapping: Map[AstNode, Node]): Unit = {
    val astToCfgConverter = new CAstToCfgConverter
    val cfg = astToCfgConverter.convert(functionDef)

    val cfgToProtoConverter =
      new CfgToProtoConverter(cfg, methodNode, methodExitNode, astToProtoMapping, bodyCpg)
    cfgToProtoConverter.convert()

  }


  private def convertMethodHeader(): (Node, Node) = {
    val methodNode = createMethodNode
    bodyCpg.addNode(methodNode)

    functionDef.getParameterList.asScala.foreach{ parameter =>
      val parameterNode = new ParameterConverter(parameter).convert(bodyCpg)
      bodyCpg.addEdge(EdgeType.AST, parameterNode, methodNode)
    }

    val methodReturnNode = createMethodReturnNode
    bodyCpg.addNode(methodReturnNode)
    bodyCpg.addEdge(EdgeType.AST, methodReturnNode, methodNode)

    (methodNode, methodReturnNode)
  }

  private def createMethodNode: Node = {
    val name = functionDef.getName
    val signature = functionDef.getReturnType.getEscapedCodeStr +
      functionDef.getParameterList.asScala.map(_.getType.getEscapedCodeStr).mkString("(", ",", ")")
    val methodNode = Node.newBuilder
      .setKey(IdPool.getNextId)
      .setType(NodeType.METHOD)
      .addStringProperty(NodePropertyName.NAME, functionDef.getName)
      .addStringProperty(NodePropertyName.FULL_NAME, s"$containingFileName:${functionDef.getName}")
      .addIntProperty(NodePropertyName.LINE_NUMBER, functionDef.getLocation.startLine)
      .addIntProperty(NodePropertyName.COLUMN_NUMBER, functionDef.getLocation.startPos)
      .addStringProperty(NodePropertyName.SIGNATURE, signature)
      /*
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_TYPE, astParentNode.getType))
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_FULL_NAME,
        astParentNode.getPropertyList.asScala.find(_.getName == NodePropertyName.FULL_NAME)
          .get.getValue.getStringValue))
          */
      .build
    methodNode
  }

  private def createMethodReturnNode: Node = {
    Node.newBuilder
      .setKey(IdPool.getNextId)
      .setType(NodeType.METHOD_RETURN)
      .addStringProperty(NodePropertyName.CODE, "RET")
      .addStringProperty(NodePropertyName.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
      .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO" )
      .addIntProperty(NodePropertyName.LINE_NUMBER, functionDef.getReturnType.getLocation.startLine)
      .addIntProperty(NodePropertyName.COLUMN_NUMBER, functionDef.getReturnType.getLocation.startPos)
      .build
  }
}
