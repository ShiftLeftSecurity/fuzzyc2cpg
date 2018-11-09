package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.cfgnew.{AstToCfgConverter, ProtoGraphAdapter}

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
    val graphAdapter = new ProtoGraphAdapter(bodyCpg, astToProtoMapping)
    val astToCfgConverter = new AstToCfgConverter(methodNode, methodExitNode, graphAdapter)
    astToCfgConverter.convert(functionDef)

  }
}
