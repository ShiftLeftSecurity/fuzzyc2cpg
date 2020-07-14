package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.Global
import io.shiftleft.passes.{DiffGraph, KeyPool, ParallelCpgPass}
import io.shiftleft.semanticcpg.language._
import org.slf4j.LoggerFactory

class CompilationUnitPass(filenames: List[String], cpg: Cpg, keyPools: Option[Iterator[KeyPool]])
    extends ParallelCpgPass[String](cpg, keyPools = keyPools) {

  private val logger = LoggerFactory.getLogger(getClass)
  val global: Global = Global()

  override def partIterator: Iterator[String] = filenames.iterator

  override def runOnPart(filename: String): Iterator[DiffGraph] = {

    val fileAndNamespaceBlock = cpg.file
      .name(new java.io.File(filename).getAbsolutePath)
      .l
      .flatMap { f =>
        f.start.namespaceBlock.l.map(n => (f, n))
      }
      .headOption

    val diffGraph = DiffGraph.newBuilder
    Iterator(diffGraph.build())
  }
}
