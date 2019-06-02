package io.shiftleft.fuzzyc2cpg

import java.nio.file.Path

import better.files.File
import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.fuzzyc2cpg.Utils.{getGlobalNamespaceBlockFullName, newEdge, newNode}
import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.slf4j.LoggerFactory
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory

class FuzzyC2Cpg[T](outputModuleFactory : CpgOutputModuleFactory[T]) {

  def this(outputPath : String) = {
    this(new OutputModuleFactory(outputPath, true, false)
      .asInstanceOf[CpgOutputModuleFactory[T]])
  }


  def runAndOutput(fileAndDirNames: Array[String]) = {
    val inputPaths = fileAndDirNames
    val sourceFileNames = SourceFiles.determine(inputPaths.toList).sorted

    val filenameToNamespaceBlock = createStructuralCpg(sourceFileNames, outputModuleFactory)
    filenameToNamespaceBlock.par.foreach(createMethodBodyCpg)
    outputModuleFactory.persist()
  }

  private def createStructuralCpg(filenames: List[String], cpgOutputModuleFactory: CpgOutputModuleFactory[T]) : List[(String, CpgStruct.Node)] = {
    val cpg = CpgStruct.newBuilder()
    addMetaDataNode(cpg)
    addAnyTypeAndNamespaceBlock(cpg)

    val filenameToNamespaceBlock =
      filenames.map{filename =>
        val pathToFile = new java.io.File(filename).toPath
        val fileNode = createFileNode(pathToFile)
        val namespaceBlock = createNamespaceBlockNode(Some(pathToFile))
        cpg.addNode(fileNode)
        cpg.addNode(namespaceBlock)
        cpg.addEdge(newEdge(EdgeType.AST, namespaceBlock, fileNode))

        filename -> namespaceBlock
      }

    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier("__structural__")
    outputModule.persistCpg(cpg)

    filenameToNamespaceBlock
  }

  private def addMetaDataNode(cpg : CpgStruct.Builder): Unit = {
    val metaNode = newNode(NodeType.META_DATA)
      .addStringProperty(NodePropertyName.LANGUAGE, Languages.C)
      .build

    cpg.addNode(metaNode)
  }

  private def addAnyTypeAndNamespaceBlock(cpg : CpgStruct.Builder): Unit = {
    val globalNamespaceBlockNotInFileNode = createNamespaceBlockNode(None)
    cpg.addNode(globalNamespaceBlockNotInFileNode)
  }


  private def createFileNode(pathToFile: Path): Node = {
    newNode(NodeType.FILE)
      .addStringProperty(NodePropertyName.NAME, pathToFile.toString)
      .build()
  }

  private def createNamespaceBlockNode(filePath: Option[Path]): Node = {
    newNode(NodeType.NAMESPACE_BLOCK)
      .addStringProperty(NodePropertyName.NAME, Defines.globalNamespaceName)
      .addStringProperty(NodePropertyName.FULL_NAME, getGlobalNamespaceBlockFullName(filePath.map(_.toString)))
      .build
  }

  def createMethodBodyCpg(filenameAndNamespaceBlock: (String, CpgStruct.Node)) = {
    val (filename, namespaceBlock) = filenameAndNamespaceBlock

  }

}

object FuzzyC2Cpg extends App {

  val DEFAULT_CPG_OUT_FILE = "cpg.bin.zip"

  private val logger = LoggerFactory.getLogger(getClass)

  parseConfig.foreach{ config =>
    try {
      new FuzzyC2Cpg(config.outputPath).runAndOutput(config.inputPaths.toArray)
    } catch {
      case exception: Exception =>
        logger.error("Failed to generate CPG.", exception)
        System.exit(1)
    }
    System.exit(0)
  }

  case class Config(inputPaths: Seq[String], outputPath: String)
  def parseConfig: Option[Config] =
    new scopt.OptionParser[Config](getClass.getSimpleName) {
      arg[String]("<input-dir>")
        .unbounded()
        .text("source directories containing C/C++ code")
        .action((x, c) => c.copy(inputPaths = c.inputPaths :+ x))
      opt[String]("out")
        .text("output filename")
        .action((x, c) => c.copy(outputPath = x))

    }.parse(args, Config(List(), DEFAULT_CPG_OUT_FILE))

}
