package io.shiftleft.fuzzyc2cpg

import java.nio.file.Paths

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule

class FunctionDefHandler(structureCpg: StructureCpg, outputModule: OutputModule) {


  def handle(ast: FunctionDefBase): Unit = {
    val methodCreator = new MethodCreator(structureCpg, ast)
    val bodyCpg = methodCreator.addMethodCpg()
    val outputFilename = generateOutputFilename(ast)
    outputModule.output(bodyCpg, outputFilename)
  }

  def generateOutputFilename(ast : FunctionDefBase) : String = {
    val path = Paths.get(Config.outputDirectory, ast.getName() + ".proto");
    return path.toString();
  }

}
