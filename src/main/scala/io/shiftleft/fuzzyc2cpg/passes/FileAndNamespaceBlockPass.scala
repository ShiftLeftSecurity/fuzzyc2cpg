package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{CpgPass, DiffGraph, KeyPool}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, nodes}
import io.shiftleft.fuzzyc2cpg.Defines
import io.shiftleft.fuzzyc2cpg.Utils.getGlobalNamespaceBlockFullName

class FileAndNamespaceBlockPass(filenames: List[String], cpg: Cpg, keyPool: Option[KeyPool] = None)
    extends CpgPass(cpg, keyPool = keyPool) {
  override def run(): Iterator[DiffGraph] = {
    implicit val diffGraph: DiffGraph.Builder = DiffGraph.newBuilder
    val absolutePaths = filenames.map(f => new java.io.File(f).toPath.toAbsolutePath.normalize().toString)
    FileAndNamespaceBlockPass.addFilesAndGlobalNamespaceBlocks(absolutePaths)
    Iterator(diffGraph.build())
  }

}

object FileAndNamespaceBlockPass {
  def addFilesAndGlobalNamespaceBlocks(filenames: List[String])(implicit diffGraph: DiffGraph.Builder): Unit = {
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
