package io.shiftleft.fuzzyc2cpg

import org.slf4j.LoggerFactory
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue

import io.shiftleft.fuzzyc2cpg.output.overflowdb.DiffGraphAndKeyPool
import io.shiftleft.passes.DiffGraph

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

case class Global(usedTypes: mutable.Set[String] = new mutable.HashSet[String])

class FuzzyC2Cpg(outputModuleFactory: CpgOutputModuleFactory) {
  import FuzzyC2Cpg.logger

  def this(outputPath: String) = {
    this(new OutputModuleFactory(outputPath, true).asInstanceOf[CpgOutputModuleFactory])
  }

  private val cache = new FuzzyC2CpgCache
  private val logger = LoggerFactory.getLogger(getClass)

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
    new CpgCreator(outputModuleFactory).runAndOutput(sourcePaths, sourceFileExtensions)
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
        val queue = new LinkedBlockingQueue[Either[CpgStruct.Builder, DiffGraphAndKeyPool]]()
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
