package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{File, NamespaceBlock}
import io.shiftleft.fuzzyc2cpg.passes.astcreation.{AntlrCModuleParserDriver, AstVisitor}
import io.shiftleft.fuzzyc2cpg.{DeclarationCache, Global}
import io.shiftleft.passes.{DiffGraph, KeyPool, ParallelCpgPass}
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

class AstCreationPass(filenames: List[String], cpg: Cpg, keyPools: Option[Iterator[KeyPool]])
    extends ParallelCpgPass[String](cpg, keyPools = keyPools) {

  private val logger = LoggerFactory.getLogger(getClass)
  val global: Global = Global()
  val cache = new DeclarationCache()

  override def partIterator: Iterator[String] = filenames.iterator

  override def runOnPart(filename: String): Iterator[DiffGraph] = {

    fileAndNamespaceBlock(filename) match {
      case Some((fileNode, namespaceBlock)) =>
        val driver = createDriver(fileNode, namespaceBlock)
        tryToParse(driver, filename)
      case None =>
        logger.warn("Invalid File/Namspace Graph")
        Iterator()
    }
  }

  private def fileAndNamespaceBlock(filename: String): Option[(File, NamespaceBlock)] = {
    cpg.file
      .name(new java.io.File(filename).getAbsolutePath)
      .l
      .flatMap { f =>
        f.start.namespaceBlock.l.map(n => (f, n))
      }
      .headOption
  }

  def createDriver(fileNode: File, namespaceBlock: NamespaceBlock): AntlrCModuleParserDriver = {
    val driver = new AntlrCModuleParserDriver()
    val astVisitor =
      new AstVisitor(driver, namespaceBlock, cache, global)
    driver.addObserver(astVisitor)
    driver.setFileNode(fileNode)
    driver
  }

  private def tryToParse(driver: AntlrCModuleParserDriver, filename: String): Iterator[DiffGraph] = {
    try {
      val diffGraph = driver.parseAndWalkFile(filename)
      Iterator(diffGraph.build)
    } catch {
      case ex: RuntimeException => {
        println("Cannot parse module: " + filename + ", skipping")
        println("Complete exception: ", ex)
        ex.printStackTrace()
        Iterator()
      }
      case _: StackOverflowError => {
        println("Cannot parse module: " + filename + ", skipping, StackOverflow")
        Iterator()
      }
    }
  }

}
