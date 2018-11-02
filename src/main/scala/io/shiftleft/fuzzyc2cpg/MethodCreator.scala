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
    val bodyVisitor = new AstToProtoConverter(functionDef, containingFileName, bodyCpg)
    bodyVisitor.convert()

    addMethodBodyCfg(bodyVisitor.getMethodNode.get,
      bodyVisitor.getMethodReturnNode.get,
      bodyVisitor.getAstToProtoMapping)

    bodyCpg
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
}
