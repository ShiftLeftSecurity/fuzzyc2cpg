package io.shiftleft.fuzzyc2cpg

import java.nio.file.{Files, Path}
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
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver

import scala.collection.mutable
import scala.util.control.NonFatal

class FuzzyC2Cpg(outputModuleFactory: CpgOutputModuleFactory) {

  private val logger = LoggerFactory.getLogger(getClass)

  def this(outputPath: String) = {
    this(
      new OutputModuleFactory(outputPath, true)
        .asInstanceOf[CpgOutputModuleFactory])
  }

  def runWithPreprocessorAndOutput(sourcePaths: List[String],
                                   includeFiles: List[String],
                                   includePaths: List[String],
                                   defines: List[String],
                                   undefines: List[String],
                                   preprocessorExecutable: String): Unit = {
    // Create temp dir to store preprocessed source.
    val preprocessedPath = Files.createTempDirectory("fuzzyc2cpg_preprocessed_")
    logger.info(s"Writing preprocessed files to [$preprocessedPath]")

    val preprocessorLogFile = Files.createTempFile("fuzzyc2cpg_preprocessor_log", ".txt").toFile
    logger.info(s"Writing preprocessor logs to [$preprocessorLogFile]")

    val sourceFileNames = SourceFiles.determine(sourcePaths)

    val cmd = Seq(preprocessorExecutable,
                  "-o", preprocessedPath.toString,
                  "-f", sourceFileNames.mkString(","),
                  "--include", includeFiles.mkString(","),
                  "-I", includePaths.mkString(","),
                  "-D", defines.mkString(","),
                  "-U", undefines.mkString(","))

    // Run preprocessor
    logger.info("Running preprocessor...")
    val process = new ProcessBuilder()
      .redirectOutput(preprocessorLogFile)
      .redirectError(preprocessorLogFile)
      .command(cmd: _*)
      .start()
    val exitCode = process.waitFor()

    if (exitCode == 0) {
      logger.info("Preprocessing complete, starting CPG generation...")
      runAndOutput(List(preprocessedPath.toString))
    } else {
      logger.error(s"Error occurred whilst running preprocessor. Exit code [$exitCode].")
    }
  }

  def runAndOutput(sourcePaths: List[String]): Unit = {
    // TODO (pp-workflow): Allow user to specify custom source file extensions.
    val sourceFileNames = SourceFiles.determine(sourcePaths).sorted

    val filenameToNodes = createStructuralCpg(sourceFileNames, outputModuleFactory)

    // TODO improve fuzzyc2cpg namespace support. Currently, everything
    // is in the same global namespace so the code below is correctly.
    filenameToNodes.par.foreach(createCpgForCompilationUnit)
    addFunctionDeclarations
    outputModuleFactory.persist()
  }

  private def addFunctionDeclarations: Unit = {
    FuzzyC2CpgCache.sortedSignatures.foreach { signature =>
      FuzzyC2CpgCache.getDeclarations(signature).foreach {
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
  private val functionDeclarations = new mutable.HashMap[String, mutable.ListBuffer[(String, CpgStruct.Builder)]]()

  /**
    * Unless `remove` has been called for `signature`, add (outputIdentifier, cpg)
    * pair to the list declarations stored for `signature`.
    * */
  def add(signature: String, outputIdentifier: String, cpg: CpgStruct.Builder): Unit = {
    functionDeclarations.synchronized {
      if (functionDeclarations.contains(signature)) {
        val declList = functionDeclarations(signature)
        if (declList.nonEmpty) {
          declList.append((outputIdentifier, cpg))
        }
      } else {
        functionDeclarations.put(signature, mutable.ListBuffer((outputIdentifier, cpg)))
      }
    }
  }

  /**
    * Register placeholder for `signature` to indicate that
    * a function definition exists for this declaration, and
    * therefore, no declaration should be written for functions
    * with this signature.
    * */
  def remove(signature: String): Unit = {
    functionDeclarations.synchronized {
      functionDeclarations.put(signature, mutable.ListBuffer())
    }
  }

  def sortedSignatures: List[String] = {
    functionDeclarations.synchronized {
      functionDeclarations.keySet.toList.sorted
    }
  }

  def getDeclarations(signature: String): List[(String, CpgStruct.Builder)] = {
    functionDeclarations.synchronized {
      functionDeclarations(signature).toList
    }
  }

}

object FuzzyC2Cpg extends App {

  private val logger = LoggerFactory.getLogger(getClass)

  parseConfig.foreach { config =>
    try {
      val fuzzyc = new FuzzyC2Cpg(config.outputPath)

      if (config.usePreprocessor) {
        fuzzyc.runWithPreprocessorAndOutput(
          config.inputPaths,
          config.includeFiles,
          config.includePaths,
          config.defines,
          config.undefines,
          config.preprocessorExecutable)
      } else {
        fuzzyc.runAndOutput(config.inputPaths)
      }

    } catch {
      case NonFatal(ex) =>
        logger.error("Failed to generate CPG.", ex)
    }
  }

  final case class Config(inputPaths: List[String] = List.empty,
                          outputPath: String = "cpg.bin.zip",
                          includeFiles: List[String] = List.empty,
                          includePaths: List[String] = List.empty,
                          defines:  List[String] = List.empty,
                          undefines: List[String] = List.empty,
                          preprocessorExecutable: String = "./fuzzypp/bin/fuzzyppcli") {
    lazy val usePreprocessor: Boolean =
      includeFiles.nonEmpty || includePaths.nonEmpty || defines.nonEmpty || undefines.nonEmpty
  }

  def parseConfig: Option[Config] =
    new scopt.OptionParser[Config](getClass.getSimpleName) {
      arg[String]("<input-dir>")
        .unbounded()
        .text("source directories containing C/C++ code")
        .action((x, c) => c.copy(inputPaths = c.inputPaths :+ x))
      opt[String]("out")
        .text("output filename")
        .action((x, c) => c.copy(outputPath = x))
      opt[String]("include")
        .unbounded()
        .text("header include files")
        .action((incl, cfg) => cfg.copy(includeFiles = cfg.includeFiles :+ incl))
      opt[String]('I', "")
        .unbounded()
        .text("header include paths")
        .action((incl, cfg) => cfg.copy(includePaths = cfg.includePaths :+ incl))
      opt[String]('D', "define")
        .unbounded()
        .text("define a name")
        .action((d, cfg) => cfg.copy(defines = cfg.defines :+ d))
      opt[String]('U', "undefine")
        .unbounded()
        .text("undefine a name")
        .action((u, cfg) => cfg.copy(undefines = cfg.undefines :+ u))
      opt[String]("preprocessor-executable")
        .text("path to the preprocessor executable")
        .action((s, cfg) => cfg.copy(preprocessorExecutable = s))
    }.parse(args, Config())

}
