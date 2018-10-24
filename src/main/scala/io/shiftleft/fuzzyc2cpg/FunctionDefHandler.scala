package io.shiftleft.fuzzyc2cpg

import java.nio.file.Paths

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node

class FunctionDefHandler(structureCpg: CpgStruct.Builder,
                         astParentNode: Node,
                         outputModule: OutputModule) {


  def handle(ast: FunctionDefBase): Unit = {
    val methodCreator = new MethodCreator(structureCpg, ast, astParentNode)
    val bodyCpg = methodCreator.addMethodCpg()
    val outputFilename = generateOutputFilename(ast)
    outputModule.output(bodyCpg, outputFilename)
  }

  def generateOutputFilename(ast : FunctionDefBase) : String = {
    val path = Paths.get(Config.outputDirectory, ast.getName() + ".proto");
    return path.toString();
  }

}
