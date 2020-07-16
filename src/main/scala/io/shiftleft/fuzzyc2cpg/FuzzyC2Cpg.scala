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
import java.util.concurrent.LinkedBlockingQueue
import io.shiftleft.passes.KeyPool

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.CollectionConverters._
import scala.util.control.NonFatal

case class Global(usedTypes: mutable.Set[String] = new mutable.HashSet[String])

class FuzzyC2Cpg(outputModuleFactory: CpgOutputModuleFactory) {
  import FuzzyC2Cpg.logger

  def this(outputPath: String) = {
    this(new OutputModuleFactory(outputPath, true).asInstanceOf[CpgOutputModuleFactory])
  }

  private val cache = new FuzzyC2CpgCache

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
    val keyPools = KeyPools.obtain(sourceFileNames.size.toLong + 2)

    val fileAndNamespaceKeyPool = keyPools.head
    val typesKeyPool = keyPools(1)
    val compilationUnitKeyPools = keyPools.slice(2, keyPools.size)

    addFilesAndNamespaces(fileAndNamespaceKeyPool)
    val global = addCompilationUnits(sourceFileNames, compilationUnitKeyPools)
    addFunctionDeclarations(cache)
    addTypeNodes(global.usedTypes, typesKeyPool)
    outputModuleFactory.persist()
  }

  private def addFilesAndNamespaces(keyPool: KeyPool): Unit = {
    val fileAndNamespaceCpg = CpgStruct.newBuilder()
    createStructuralCpg(keyPool, fileAndNamespaceCpg)
    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier("__structural__")
    outputModule.persistCpg(fileAndNamespaceCpg)
  }

  // TODO improve fuzzyc2cpg namespace support. Currently, everything
  // is in the same global namespace so the code below is correct.
  private def addCompilationUnits(sourceFileNames: List[String], keyPools: List[KeyPool]): Global = {
    val global = Global()
    sourceFileNames.zipWithIndex
      .map { case (filename, i) => (filename, keyPools(i)) }
      .par
      .foreach { case (filename, keyPool) => createCpgForCompilationUnit(filename, keyPool, global) }
    global
  }

  private def addFunctionDeclarations(cache: FuzzyC2CpgCache): Unit = {
    cache.sortedSignatures.par.foreach { signature =>
      cache.getDeclarations(signature).foreach {
        case (outputIdentifier, bodyCpg) =>
          val outputModule = outputModuleFactory.create()
          outputModule.setOutputIdentifier(outputIdentifier)
          outputModule.persistCpg(bodyCpg)
      }
    }
  }

  private def addTypeNodes(usedTypes: mutable.Set[String], keyPool: KeyPool): Unit = {
    val cpg = CpgStruct.newBuilder()
    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier("__types__")
    createTypeNodes(usedTypes, keyPool, cpg)
    outputModule.persistCpg(cpg)
  }

  private def fileAndNamespaceGraph(filename: String, keyPool: KeyPool): (Node, Node) = {

    def createFileNode(pathToFile: Path, keyPool: KeyPool): Node = {
      newNode(NodeType.FILE)
        .setKey(keyPool.next)
        .addStringProperty(NodePropertyName.NAME, pathToFile.toAbsolutePath.normalize.toString)
        .build()
    }

    val cpg = CpgStruct.newBuilder()
    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier(filename + " fileAndNamespace")

    val pathToFile = new java.io.File(filename).toPath
    val fileNode = createFileNode(pathToFile, keyPool)
    val namespaceBlock = createNamespaceBlockNode(Some(pathToFile), keyPool)
    cpg.addNode(fileNode)
    cpg.addNode(namespaceBlock)
    cpg.addEdge(newEdge(EdgeType.AST, namespaceBlock, fileNode))
    outputModule.persistCpg(cpg)
    (fileNode, namespaceBlock)
  }

  private def createNamespaceBlockNode(filePath: Option[Path], keyPool: KeyPool): Node = {
    newNode(NodeType.NAMESPACE_BLOCK)
      .setKey(keyPool.next)
      .addStringProperty(NodePropertyName.NAME, Defines.globalNamespaceName)
      .addStringProperty(NodePropertyName.FULL_NAME, getGlobalNamespaceBlockFullName(filePath.map(_.toString)))
      .build
  }

  private def createTypeNodes(usedTypes: mutable.Set[String], keyPool: KeyPool, cpg: CpgStruct.Builder): Unit = {
    usedTypes.toList.sorted
      .foreach { typeName =>
        val node = newNode(NodeType.TYPE)
          .setKey(keyPool.next)
          .addStringProperty(NodePropertyName.NAME, typeName)
          .addStringProperty(NodePropertyName.FULL_NAME, typeName)
          .addStringProperty(NodePropertyName.TYPE_DECL_FULL_NAME, typeName)
          .build
        cpg.addNode(node)
      }
  }

  private def createStructuralCpg(keyPool: KeyPool, cpg: CpgStruct.Builder): Unit = {

    def addMetaDataNode(cpg: CpgStruct.Builder): Unit = {
      val metaNode = newNode(NodeType.META_DATA)
        .setKey(keyPool.next)
        .addStringProperty(NodePropertyName.LANGUAGE, Languages.C)
        .build
      cpg.addNode(metaNode)
    }

    def addAnyTypeAndNamespaceBlock(cpg: CpgStruct.Builder): Unit = {
      val globalNamespaceBlockNotInFileNode = createNamespaceBlockNode(None, keyPool)
      cpg.addNode(globalNamespaceBlockNotInFileNode)
    }

    addMetaDataNode(cpg)
    addAnyTypeAndNamespaceBlock(cpg)
  }

  private def createCpgForCompilationUnit(filename: String, keyPool: KeyPool, global: Global): Unit = {
    val (fileNode, namespaceBlock) = fileAndNamespaceGraph(filename, keyPool)

    // We call the module parser here and register the `astVisitor` to
    // receive callbacks as we walk the tree. The method body parser
    // will the invoked by `astVisitor` as we walk the tree

    val driver = new AntlrCModuleParserDriver()
    val astVisitor =
      new AstVisitor(outputModuleFactory, namespaceBlock, keyPool, cache, global)
    driver.addObserver(astVisitor)
    driver.setKeyPool(keyPool)
    driver.setOutputModuleFactory(outputModuleFactory)
    driver.setFileNode(fileNode)

    try {
      driver.parseAndWalkFile(filename)
    } catch {
      case ex: RuntimeException => {
        logger.warn("Cannot parse module: " + filename + ", skipping")
        logger.warn("Complete exception: ", ex)
      }
      case _: StackOverflowError => {
        logger.warn("Cannot parse module: " + filename + ", skipping, StackOverflow")
      }
    }
  }

}

object FuzzyC2Cpg {

  private val logger = LoggerFactory.getLogger(classOf[FuzzyC2Cpg])

  def main(args: Array[String]): Unit = {
    parseConfig(args).foreach { config =>
      try {

        val factory = if (!config.overflowDb) {
          new OutputModuleFactory(config.outputPath, true)
            .asInstanceOf[CpgOutputModuleFactory]
        } else {
          val queue = new LinkedBlockingQueue[CpgStruct.Builder]()
          new io.shiftleft.fuzzyc2cpg.output.overflowdb.OutputModuleFactory(config.outputPath, queue)
        }

        val fuzzyc = new FuzzyC2Cpg(factory)

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
  }

  final case class Config(inputPaths: Set[String] = Set.empty,
                          outputPath: String = "cpg.bin.zip",
                          sourceFileExtensions: Set[String] = Set(".c", ".cc", ".cpp", ".h", ".hpp"),
                          includeFiles: Set[String] = Set.empty,
                          includePaths: Set[String] = Set.empty,
                          defines: Set[String] = Set.empty,
                          undefines: Set[String] = Set.empty,
                          preprocessorExecutable: String = "./fuzzypp/bin/fuzzyppcli",
                          overflowDb: Boolean = false) {
    lazy val usePreprocessor: Boolean =
      includeFiles.nonEmpty || includePaths.nonEmpty || defines.nonEmpty || undefines.nonEmpty
  }

  def parseConfig(args: Array[String]): Option[Config] =
    new scopt.OptionParser[Config](classOf[FuzzyC2Cpg].getSimpleName) {
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
      opt[Unit]("overflowdb")
        .text("create overflowdb")
        .action((_, cfg) => cfg.copy(overflowDb = true))
    }.parse(args, Config())

}
