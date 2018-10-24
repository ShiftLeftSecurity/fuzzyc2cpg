package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterBase
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import Utils._
import io.shiftleft.codepropertygraph.generated.EvaluationStrategies

class ParameterConverter(parameter: ParameterBase) {
  def convert(targetCpg: CpgStruct.Builder): Node = {
    val parameterNode = Node.newBuilder.setKey(IdPool.getNextId)
      .setType(NodeType.METHOD_PARAMETER_IN)
      .addStringProperty(NodePropertyName.CODE, parameter.getEscapedCodeStr)
      .addStringProperty(NodePropertyName.NAME, parameter.getName)
      .addIntProperty(NodePropertyName.ORDER, parameter.getChildNumber + 1)
      .addStringProperty(NodePropertyName.EVALUATION_STRATEGY, EvaluationStrategies.BY_VALUE)
      .addStringProperty(NodePropertyName.TYPE_FULL_NAME, "TODO")
      .addIntProperty(NodePropertyName.LINE_NUMBER, parameter.getLocation.startLine)
      .addIntProperty(NodePropertyName.COLUMN_NUMBER, parameter.getLocation.startPos)
      .build

    targetCpg.addNode(parameterNode)
    parameterNode
  }
}
