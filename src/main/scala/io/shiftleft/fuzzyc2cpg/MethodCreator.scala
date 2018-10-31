package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.EvaluationStrategies
import io.shiftleft.fuzzyc2cpg.ast.functionDef.{FunctionDefBase, ParameterBase, ReturnType}
import io.shiftleft.fuzzyc2cpg.cfg.{CAstToCfgConverter, CFG}
import io.shiftleft.fuzzyc2cpg.cfg.nodes._
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType

import scala.collection.JavaConverters._

class MethodCreator(functionDef: FunctionDefBase,
                    astParentNode: Node,
                    containingFileName: String) {
  private var nodeToProtoNode = Map[CfgNode, CpgStruct.Node]()
  private val bodyCpg = CpgStruct.newBuilder()
  val cfg = initializeCfg(functionDef)

  def addMethodCpg(): CpgStruct.Builder = {
    val methodNode = convertMethodHeader(bodyCpg)
    val bodyBlockNode = addMethodBodyCpg(bodyCpg)
    bodyCpg.addEdge(EdgeType.AST, bodyBlockNode, methodNode)
  }

  private def addMethodBodyCpg(targetCpg: CpgStruct.Builder): Node = {
    val bodyVisitor = new MethodBodyVisitor(functionDef)
    bodyVisitor.convert(targetCpg)
  }


  private def initializeCfg(ast: FunctionDefBase): CFG = {
    val converter = new CAstToCfgConverter
    val cfg = converter.convert(ast)

    cfg
  }

  private def convertMethodHeader(targetCpg: CpgStruct.Builder): Node = {
    val methodNode = createMethodNode
    targetCpg.addNode(methodNode)

    functionDef.getParameterList.asScala.foreach{ parameter =>
      val parameterNode = new ParameterConverter(parameter).convert(targetCpg)
      targetCpg.addEdge(EdgeType.AST, parameterNode, methodNode)
    }

    val methodReturnNode = createMethodReturnNode
    targetCpg.addNode(methodReturnNode)
    targetCpg.addEdge(EdgeType.AST, methodReturnNode, methodNode)

    methodNode
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
