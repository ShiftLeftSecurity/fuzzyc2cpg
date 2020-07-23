package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{CpgPass, DiffGraph}
import io.shiftleft.semanticcpg.language._

/**
  * A pass that ensures that for any method m for which a body exists,
  * there are no more method stubs for corresponding declarations.
  * */
class StubRemovalPass(cpg: Cpg) extends CpgPass(cpg) {
  override def run(): Iterator[DiffGraph] = {

    val sigToMethodWithDef = cpg.method.isNotStub.map(m => (m.signature -> true)).toMap

    cpg.method.isStub.toList
      .filter(m => sigToMethodWithDef.contains(m.signature))
      .iterator
      .map { stub =>
        val diffGraph = DiffGraph.newBuilder
        stub.start.ast.foreach(diffGraph.removeNode)
        diffGraph.build
      }
  }
}
