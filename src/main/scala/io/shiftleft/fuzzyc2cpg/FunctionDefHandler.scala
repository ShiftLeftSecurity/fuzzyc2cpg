package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.astnew.{AstToCpgConverter, ProtoCpgAdapter}
import io.shiftleft.fuzzyc2cpg.cfg.{AstToCfgConverter, ProtoGraphAdapter}
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node

class FunctionDefHandler(outputModule: CpgOutputModule,
                         containingFileName: String) {
  private val bodyCpg = CpgStruct.newBuilder()

  def handle(functionDef: FunctionDef): Unit = {
    addMethodCpg(functionDef)
    outputModule.persistCpg(bodyCpg)
  }

  private def addMethodCpg(functionDef: FunctionDef): CpgStruct.Builder = {
    val cpgAdapter = new ProtoCpgAdapter(bodyCpg)
    val bodyVisitor = new AstToCpgConverter(containingFileName, cpgAdapter)
    bodyVisitor.convert(functionDef)

    addMethodBodyCfg(functionDef,
      bodyVisitor.getMethodNode.get,
      bodyVisitor.getMethodReturnNode.get,
      bodyVisitor.getAstToProtoMapping)

    bodyCpg
  }

  private def addMethodBodyCfg(functionDef: FunctionDef,
                               methodNode: Node,
                               methodExitNode: Node,
                               astToProtoMapping: Map[AstNode, Node]): Unit = {
    val graphAdapter = new ProtoGraphAdapter(bodyCpg, astToProtoMapping)
    val astToCfgConverter = new AstToCfgConverter(methodNode, methodExitNode, graphAdapter)
    astToCfgConverter.convert(functionDef)

  }
}
