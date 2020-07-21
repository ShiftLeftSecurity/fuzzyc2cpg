package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{CpgPass, DiffGraph, KeyPool}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, nodes}
import io.shiftleft.fuzzyc2cpg.Defines
import io.shiftleft.fuzzyc2cpg.Utils.getGlobalNamespaceBlockFullName

/**
  * For a given list of file names, this pass creates corresponding FILE
  * and NAMESPACE_BLOCK nodes, as well as SOURCE_FILE edges from files to their
  * namespace blocks. We currently ignore namespaces as specified by the C++
  * key word `namespace` and instead just create one namespace per file.
  * */
class FileAndNamespaceBlockPass(filenames: List[String], cpg: Cpg, keyPool: Option[KeyPool] = None)
    extends CpgPass(cpg, keyPool = keyPool) {

  override def run(): Iterator[DiffGraph] = {
    val diffGraph: DiffGraph.Builder = DiffGraph.newBuilder
    val absolutePaths = filenames.map(f => new java.io.File(f).toPath.toAbsolutePath.normalize().toString)
    addFilesAndGlobalNamespaceBlocks(absolutePaths, diffGraph)
    Iterator(diffGraph.build())
  }

  def addFilesAndGlobalNamespaceBlocks(filenames: List[String], diffGraph: DiffGraph.Builder): Unit = {
    filenames.map(f => nodes.NewFile(name = f)).foreach { fileNode =>
      diffGraph.addNode(fileNode)
      val namespaceBlock = nodes.NewNamespaceBlock(
        name = Defines.globalNamespaceName,
        fullName = getGlobalNamespaceBlockFullName(Some(fileNode.name))
      )
      diffGraph.addNode(fileNode)
      diffGraph.addNode(namespaceBlock)
      diffGraph.addEdge(namespaceBlock, fileNode, EdgeTypes.SOURCE_FILE)
    }
  }

}
