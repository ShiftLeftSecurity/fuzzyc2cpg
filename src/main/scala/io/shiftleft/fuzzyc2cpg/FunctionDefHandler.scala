package io.shiftleft.fuzzyc2cpg

import java.nio.file.Paths

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule

class FunctionDefHandler(structureCpg: StructureCpg, outputModule: OutputModule) {

  val methodCreator = new MethodCreator(structureCpg)

  def handle(ast: FunctionDefBase): Unit = {

    val methodNode = methodCreator.addMethodStubToStructureCpg(ast)
    val bodyCpg = methodCreator.addMethodBodyCpg(methodNode, ast)
    val outputFilename = generateOutputFilename(ast)
    outputModule.output(bodyCpg, outputFilename)
  }

  def generateOutputFilename(ast : FunctionDefBase) : String = {
    val path = Paths.get(Config.outputDirectory, ast.getName() + ".proto");
    return path.toString();
  }

}
