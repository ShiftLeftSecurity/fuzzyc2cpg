package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{DiffGraph, ParallelCpgPass}
import io.shiftleft.semanticcpg.language._
import io.shiftleft.codepropertygraph.generated.nodes

/**
  * A pass that ensures that for any method m for which a body exists,
  * there are no more method stubs for corresponding declarations.
  * */
class StubRemovalPass(cpg: Cpg) extends ParallelCpgPass[nodes.Method](cpg) {
  override def partIterator: Iterator[nodes.Method] =
    cpg.method.isNotStub.iterator

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] = {
    val diffGraph = DiffGraph.newBuilder
    cpg.method.isStub.where(m => m.signature == method.signature).foreach { stubMethod =>
      stubMethod.ast.l.foreach { node =>
        diffGraph.removeNode(node.id2())
      }
    }
    Iterator(diffGraph.build)
  }
}
