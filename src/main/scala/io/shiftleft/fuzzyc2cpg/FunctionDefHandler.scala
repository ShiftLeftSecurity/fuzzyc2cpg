package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node

class FunctionDefHandler(astParentNode: Node,
                         outputModule: CpgOutputModule,
                         containingFileName: String) {


  def handle(ast: FunctionDefBase): Unit = {
    val methodCreator = new MethodCreator(ast, astParentNode, containingFileName)
    val bodyCpg = methodCreator.addMethodCpg()
    outputModule.persistCpg(bodyCpg)
  }
}
