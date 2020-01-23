package io.shiftleft.fuzzyc2cpg

import org.slf4j.LoggerFactory

import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.fuzzyc2cpg.Utils.{getGlobalNamespaceBlockFullName, newEdge, newNode, _}
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}

import java.nio.file.{Files, Path}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.util.control.NonFatal

class FuzzyC2Cpg(outputModuleFactory: CpgOutputModuleFactory) {

  private val logger = LoggerFactory.getLogger(getClass)

  def this(outputPath: String) = {
    this(
      new OutputModuleFactory(outputPath, true)
        .asInstanceOf[CpgOutputModuleFactory])
  }

  def runWithPreprocessorAndOutput(sourcePaths: Set[String],
                                   sourceFileExtensions: Set[String],
                                   includeFiles: Set[String],
                                   includePaths: Set[String],
                                   defines: Set[String],
                                   undefines: Set[String],
                                   preprocessorExecutable: String): Unit = {
    // Create temp dir to store preprocessed source.
    val preprocessedPath = Files.createTempDirectory("fuzzyc2cpg_preprocessed_")
    logger.info(s"Writing preprocessed files to [$preprocessedPath]")

    val preprocessorLogFile = Files.createTempFile("fuzzyc2cpg_preprocessor_log", ".txt").toFile
    logger.info(s"Writing preprocessor logs to [$preprocessorLogFile]")

    val sourceFileNames = SourceFiles.determine(sourcePaths, sourceFileExtensions)

    val commandBuffer = new ListBuffer[String]()
    commandBuffer.appendAll(List(preprocessorExecutable, "--verbose", "-o", preprocessedPath.toString))
    if (sourceFileNames.nonEmpty) commandBuffer.appendAll(List("-f", sourceFileNames.mkString(",")))
    if (includeFiles.nonEmpty) commandBuffer.appendAll(List("--include", includeFiles.mkString(",")))
    if (includePaths.nonEmpty) commandBuffer.appendAll(List("-I", includePaths.mkString(",")))
    if (defines.nonEmpty) commandBuffer.appendAll(List("-D", defines.mkString(",")))
    if (undefines.nonEmpty) commandBuffer.appendAll(List("-U", defines.mkString(",")))

    val cmd = commandBuffer.toList

    // Run preprocessor
    logger.info("Running preprocessor...")
    val process = new ProcessBuilder()
      .redirectOutput(preprocessorLogFile)
      .redirectError(preprocessorLogFile)
      .command(cmd: _*)
      .start()
    val exitCode = process.waitFor()

    if (exitCode == 0) {
      logger.info(s"Preprocessing complete, files written to [$preprocessedPath], starting CPG generation...")
      runAndOutput(Set(preprocessedPath.toString), sourceFileExtensions)
    } else {
      logger.error(
        s"Error occurred whilst running preprocessor. Log written to [$preprocessorLogFile]. Exit code [$exitCode].")
    }
  }

  def runAndOutput(sourcePaths: Set[String], sourceFileExtensions: Set[String]): Unit = {
    val sourceFileNames = SourceFiles.determine(sourcePaths, sourceFileExtensions)

    val filenameToNodes = createStructuralCpg(sourceFileNames)

    // TODO improve fuzzyc2cpg namespace support. Currently, everything
    // is in the same global namespace so the code below is correctly.
    filenameToNodes.par.foreach(createCpgForCompilationUnit)
    addFunctionDeclarations
    outputModuleFactory.persist()
  }

  private def addFunctionDeclarations(): Unit = {
    FuzzyC2CpgCache.sortedSignatures.foreach { signature =>
      FuzzyC2CpgCache.getDeclarations(signature).foreach {
        case (outputIdentifier, bodyCpg) =>
          val outputModule = outputModuleFactory.create()
          outputModule.setOutputIdentifier(outputIdentifier)
          outputModule.persistCpg(bodyCpg)
      }
    }
  }

  private def createStructuralCpg(filenames: Set[String]): Set[(String, NodesForFile)] = {

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
        .addStringProperty(NodePropertyName.NAME, pathToFile.toAbsolutePath.normalize.toString)
        .build()
    }

    def createNodesForFiles(cpg: CpgStruct.Builder): Set[(String, NodesForFile)] =
      filenames.map { filename =>
        val pathToFile = new java.io.File(filename).toPath
        val fileNode = createFileNode(pathToFile)
        val namespaceBlock = createNamespaceBlockNode(Some(pathToFile))
        cpg.addNode(fileNode)
        cpg.addNode(namespaceBlock)
        cpg.addEdge(newEdge(EdgeType.AST, namespaceBlock, fileNode))
        filename -> NodesForFile(fileNode, namespaceBlock)
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

  def createCpgForCompilationUnit(filenameAndNodes: (String, NodesForFile)): Unit = {
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

    try {
      driver.parseAndWalkFile(filename)
    } catch {
      case ex: RuntimeException => {
        logger.warn("Cannot parse module: " + filename + ", skipping")
        logger.warn("Complete exception: ", ex)
        return
      }
    }

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
      functionDeclarations.remove(signature)
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
        fuzzyc.runWithPreprocessorAndOutput(config.inputPaths,
                                            config.sourceFileExtensions,
                                            config.includeFiles,
                                            config.includePaths,
                                            config.defines,
                                            config.undefines,
                                            config.preprocessorExecutable)
      } else {
        fuzzyc.runAndOutput(config.inputPaths, config.sourceFileExtensions)
      }

    } catch {
      case NonFatal(ex) =>
        logger.error("Failed to generate CPG.", ex)
    }
  }

  final case class Config(inputPaths: Set[String] = Set.empty,
                          outputPath: String = "cpg.bin.zip",
                          sourceFileExtensions: Set[String] = Set(".c", ".cc", ".cpp", ".h", ".hpp"),
                          includeFiles: Set[String] = Set.empty,
                          includePaths: Set[String] = Set.empty,
                          defines: Set[String] = Set.empty,
                          undefines: Set[String] = Set.empty,
                          preprocessorExecutable: String = "./fuzzypp/bin/fuzzyppcli") {
    lazy val usePreprocessor: Boolean =
      includeFiles.nonEmpty || includePaths.nonEmpty || defines.nonEmpty || undefines.nonEmpty
  }

  def parseConfig: Option[Config] =
    new scopt.OptionParser[Config](getClass.getSimpleName) {
      arg[String]("<input-dir>")
        .unbounded()
        .text("source directories containing C/C++ code")
        .action((x, c) => c.copy(inputPaths = c.inputPaths + x))
      opt[String]("out")
        .text("(DEPRECATED use `output`) output filename")
        .action { (x, c) =>
          logger.warn("`--out` is DEPRECATED. Use `--output` instead")
          c.copy(outputPath = x)
        }
      opt[String]("output")
        .abbr("o")
        .text("output filename")
        .action((x, c) => c.copy(outputPath = x))
      opt[String]("source-file-ext")
        .unbounded()
        .text("source file extensions to include when gathering source files. Defaults are .c, .cc, .cpp, .h and .hpp")
        .action((pat, cfg) => cfg.copy(sourceFileExtensions = cfg.sourceFileExtensions + pat))
      opt[String]("include")
        .unbounded()
        .text("header include files")
        .action((incl, cfg) => cfg.copy(includeFiles = cfg.includeFiles + incl))
      opt[String]('I', "")
        .unbounded()
        .text("header include paths")
        .action((incl, cfg) => cfg.copy(includePaths = cfg.includePaths + incl))
      opt[String]('D', "define")
        .unbounded()
        .text("define a name")
        .action((d, cfg) => cfg.copy(defines = cfg.defines + d))
      opt[String]('U', "undefine")
        .unbounded()
        .text("undefine a name")
        .action((u, cfg) => cfg.copy(undefines = cfg.undefines + u))
      opt[String]("preprocessor-executable")
        .text("path to the preprocessor executable")
        .action((s, cfg) => cfg.copy(preprocessorExecutable = s))
      help("help").text("display this help message")
    }.parse(args, Config())

}
