package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.cfg.{AstToCfgConverter, ProtoGraphAdapter}
import io.shiftleft.fuzzyc2cpg.astnew.{AstToCpgConverter, ProtoCpgAdapter}

class MethodCreator(functionDef: FunctionDefBase,
                    containingFileName: String) {
  private val bodyCpg = CpgStruct.newBuilder()

  def addMethodCpg(): CpgStruct.Builder = {
    val cpgAdapter = new ProtoCpgAdapter(bodyCpg)
    val bodyVisitor = new AstToCpgConverter(containingFileName, cpgAdapter)
    bodyVisitor.convert(functionDef)

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
