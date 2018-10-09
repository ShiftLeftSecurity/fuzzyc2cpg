package io.shiftleft.fuzzyc2cpg

import java.nio.file.{Path, Paths}

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule

class FunctionDefHandler(structureCpg: StructureCpg, outputModule: OutputModule) {

  val stubBuilder = new MethodStubCreator(structureCpg)
  val bodyCreator = new MethodBodyCreator()

  def handle(ast: FunctionDefBase): Unit = {

    val methodNode = stubBuilder.addMethodStubToStructureCpg(ast)
    val bodyCpg = bodyCreator.addMethodBodyCpg(methodNode, ast)
    val outputFilename = generateOutputFilename(ast)
    outputModule.output(bodyCpg, outputFilename)
  }

  def generateOutputFilename(ast : FunctionDefBase) : String = {
    val path = Paths.get(Config.outputDirectory, ast.getName() + ".proto");
    return path.toString();
  }

}
