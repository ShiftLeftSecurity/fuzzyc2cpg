package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.Utils.newStringProperty
import io.shiftleft.fuzzyc2cpg.ast.functionDef.{FunctionDefBase, ParameterBase}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}
import scala.collection.JavaConverters._

class MethodStubCreator(structureCpg : StructureCpg) {

  def addMethodStubToStructureCpg(functionDef: FunctionDefBase) = {
    val name = functionDef.getName
    val methodNode = Node.newBuilder.setKey(IdPool.getNextId)
      .setType(NodeType.METHOD)
      .addProperty(newStringProperty(NodePropertyName.NAME, name))
      .addProperty(newStringProperty(NodePropertyName.FULL_NAME, name)).build

    structureCpg.addNode(methodNode)
    connectMethodToNamespaceAndType(methodNode)
    functionDef.getParameterList.asScala.foreach{ parameter =>
      addParameterCpg(parameter)
    }
    methodNode
  }

  private def connectMethodToNamespaceAndType(methodNode: CpgStruct.Node): Unit = {
    structureCpg.addEdge(
      CpgStruct.Edge.newBuilder.setType(EdgeType.AST)
        .setSrc(structureCpg.getNamespaceBlockNode.getKey)
        .setDst(methodNode.getKey).build)
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
