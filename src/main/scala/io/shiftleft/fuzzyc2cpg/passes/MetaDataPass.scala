package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.{Languages, nodes}
import io.shiftleft.fuzzyc2cpg.Defines
import io.shiftleft.fuzzyc2cpg.Utils.getGlobalNamespaceBlockFullName
import io.shiftleft.passes.{CpgPass, DiffGraph, KeyPool}

class MetaDataPass(cpg: Cpg, keyPool: Option[KeyPool] = None) extends CpgPass(cpg, keyPool = keyPool) {
  override def run(): Iterator[DiffGraph] = {
    def addMetaDataNode(diffGraph: DiffGraph.Builder): Unit = {
      val metaNode = nodes.NewMetaData(language = Languages.C)
      diffGraph.addNode(metaNode)
    }

    def addAnyNamespaceBlock(diffGraph: DiffGraph.Builder): Unit = {
      val node = nodes.NewNamespaceBlock(
        name = Defines.globalNamespaceName,
        fullName = getGlobalNamespaceBlockFullName(None)
      )
      diffGraph.addNode(node)
    }

    val diffGraph = DiffGraph.newBuilder
    addMetaDataNode(diffGraph)
    addAnyNamespaceBlock(diffGraph)
    Iterator(diffGraph.build())
  }
}
