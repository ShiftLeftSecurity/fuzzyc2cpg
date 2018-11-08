package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.cfg.CAstToCfgConverter
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.cfgnew.AstToCfgConverter

class MethodCreator(functionDef: FunctionDefBase,
                    astParentNode: Node,
                    containingFileName: String) {
  private val bodyCpg = CpgStruct.newBuilder()

  def addMethodCpg(): CpgStruct.Builder = {
    val bodyVisitor = new AstToProtoConverter(functionDef, containingFileName, bodyCpg)
    bodyVisitor.convert()

    addMethodBodyCfgNew(bodyVisitor.getMethodNode.get,
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

  private def addMethodBodyCfgNew(methodNode: Node,
                               methodExitNode: Node,
                               astToProtoMapping: Map[AstNode, Node]): Unit = {
    val astToCfgConverter = new AstToCfgConverter(methodNode, methodExitNode, astToProtoMapping, bodyCpg)
    astToCfgConverter.convert(functionDef)

  }
}
