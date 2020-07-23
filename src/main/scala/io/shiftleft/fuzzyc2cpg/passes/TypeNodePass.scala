package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.Global
import io.shiftleft.passes.{CpgPass, DiffGraph}
import io.shiftleft.codepropertygraph.generated.nodes

class TypeNodePass(global: Global, cpg: Cpg) extends CpgPass(cpg) {
  override def run(): Iterator[DiffGraph] = {
    val diffGraph = DiffGraph.newBuilder
    global.usedTypes.toList.sorted.foreach { typeName =>
      val node = nodes.NewType(
        name = typeName,
        fullName = typeName,
        typeDeclFullName = typeName
      )
      diffGraph.addNode(node)
    }
    Iterator(diffGraph.build)
  }
}
