package io.shiftleft.fuzzyc2cpg

import java.nio.file.Path

import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.fuzzyc2cpg.Utils.{getGlobalNamespaceBlockFullName, newEdge, newNode}
import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.slf4j.LoggerFactory
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver

import scala.collection.mutable

class FuzzyC2Cpg(outputModuleFactory: CpgOutputModuleFactory) {

  def this(outputPath: String) = {
    this(
      new OutputModuleFactory(outputPath, true, false)
        .asInstanceOf[CpgOutputModuleFactory])
  }

  def runAndOutput(fileAndDirNames: Array[String]) = {
    val inputPaths = fileAndDirNames
    val sourceFileNames = SourceFiles.determine(inputPaths.toList).sorted

    val filenameToNodes = createStructuralCpg(sourceFileNames, outputModuleFactory)

    // TODO improve fuzzyc2cpg namespace support. Currently, everything
    // is in the same global namespace so the code below is correctly.
    filenameToNodes.par.foreach(createCpgForCompilationUnit)
    addEmptyFunctions
    outputModuleFactory.persist()
  }

  def addEmptyFunctions = {
    FuzzyC2CpgCache.sortedKeySet.foreach { signature =>
      FuzzyC2CpgCache.get(signature).foreach {
        case (outputIdentifier, bodyCpg) =>
          val outputModule = outputModuleFactory.create()
          outputModule.setOutputIdentifier(outputIdentifier)
          outputModule.persistCpg(bodyCpg)
      }
    }
  }

  private def createStructuralCpg(filenames: List[String],
                                  cpgOutputModuleFactory: CpgOutputModuleFactory): List[(String, NodesForFile)] = {

    def addMetaDataNode(cpg: CpgStruct.Builder): Unit = {
      val metaNode = newNode(NodeType.META_DATA)
        .addStringProperty(NodePropertyName.LANGUAGE, Languages.C)
        .build
      cpg.addNode(metaNode)
    }

    def addAnyTypeAndNamespaceBlock(cpg: CpgStruct.Builder): Unit = {
      val globalNamespaceBlockNotInFileNode = createNamespaceBlockNode(None)
      cpg.addNode(globalNamespaceBlockNotInFileNode)
    }

    def createFileNode(pathToFile: Path): Node = {
      newNode(NodeType.FILE)
        .addStringProperty(NodePropertyName.NAME, pathToFile.toString)
        .build()
    }

    def createNodesForFiles(cpg: CpgStruct.Builder): List[(String, NodesForFile)] =
      filenames.map { filename =>
        val pathToFile = new java.io.File(filename).toPath
        val fileNode = createFileNode(pathToFile)
        val namespaceBlock = createNamespaceBlockNode(Some(pathToFile))
        cpg.addNode(fileNode)
        cpg.addNode(namespaceBlock)
        cpg.addEdge(newEdge(EdgeType.AST, namespaceBlock, fileNode))
        filename -> new NodesForFile(fileNode, namespaceBlock)
      }

    val cpg = CpgStruct.newBuilder()
    addMetaDataNode(cpg)
    addAnyTypeAndNamespaceBlock(cpg)
    val filenameToNodes = createNodesForFiles(cpg)
    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier("__structural__")
    outputModule.persistCpg(cpg)
    filenameToNodes
  }

  case class NodesForFile(fileNode: CpgStruct.Node, namespaceBlockNode: CpgStruct.Node) {}

  private def createNamespaceBlockNode(filePath: Option[Path]): Node = {
    newNode(NodeType.NAMESPACE_BLOCK)
      .addStringProperty(NodePropertyName.NAME, Defines.globalNamespaceName)
      .addStringProperty(NodePropertyName.FULL_NAME, getGlobalNamespaceBlockFullName(filePath.map(_.toString)))
      .build
  }

  def createCpgForCompilationUnit(filenameAndNodes: (String, NodesForFile)) = {
    val (filename, nodesForFile) = filenameAndNodes
    val (fileNode, namespaceBlock) = (nodesForFile.fileNode, nodesForFile.namespaceBlockNode)
    val cpg = CpgStruct.newBuilder

    // We call the module parser here and register the `astVisitor` to
    // receive callbacks as we walk the tree. The method body parser
    // will the invoked by `astVisitor` as we walk the tree

    val driver = new AntlrCModuleParserDriver()
    val astVisitor =
      new AstVisitor(outputModuleFactory, cpg, namespaceBlock)
    driver.addObserver(astVisitor)
    driver.setOutputModuleFactory(outputModuleFactory);
    driver.setCpg(cpg);
    driver.setNamespaceBlock(namespaceBlock);
    driver.setFileNode(fileNode)
    driver.parseAndWalkFile(filename)

    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier(
      s"$filename types"
    )
    outputModule.persistCpg(cpg)
  }

}

object FuzzyC2CpgCache {
  private val emptyFunctions = new mutable.HashMap[String, Option[(String, CpgStruct.Builder)]]()

  def registerEmptyFunctionOrRemove(functionDef: FunctionDef,
                                    outputIdentifier: String,
                                    bodyCpg: CpgStruct.Builder): Boolean = {
    emptyFunctions.synchronized {
      val signature = functionDef.getFunctionSignature
      // If this is an empty method, do not persist it yet, just store it
      if (functionDef.getContent.getStatements.size() == 0) {
        if (!emptyFunctions.contains(signature)) {
          emptyFunctions.put(signature, Some(outputIdentifier, bodyCpg))
        }
        false
      } else {
        // We've just encountered a non-empty function, so, put a 'None'
        // into emptyFunctions for that signature
        emptyFunctions.put(signature, None)
        true
      }
    }
  }

  def sortedKeySet: List[String] = {
    emptyFunctions.synchronized {
      FuzzyC2CpgCache.emptyFunctions.keySet.toList.sorted
    }
  }

  def get(signature: String): Option[(String, CpgStruct.Builder)] = {
    emptyFunctions.synchronized {
      FuzzyC2CpgCache.emptyFunctions(signature)
    }
  }

}

object FuzzyC2Cpg extends App {

  val DEFAULT_CPG_OUT_FILE = "cpg.bin.zip"

  private val logger = LoggerFactory.getLogger(getClass)

  parseConfig.foreach { config =>
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
