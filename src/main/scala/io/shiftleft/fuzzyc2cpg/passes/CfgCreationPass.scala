package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.{DiffGraph, KeyPool, ParallelCpgPass}
import io.shiftleft.codepropertygraph.generated.nodes
import io.shiftleft.semanticcpg.language._

import scala.collection.mutable

class CfgCreationPass(cpg: Cpg, keyPools: Option[Iterator[KeyPool]])
    extends ParallelCpgPass[nodes.Method](cpg, keyPools = keyPools) {

  override def partIterator: Iterator[nodes.Method] =
    cpg.method.iterator

  override def runOnPart(method: nodes.Method): Iterator[DiffGraph] = {
    val diffGraph = DiffGraph.newBuilder
    val queue: mutable.Queue[nodes.AstNode] = mutable.Queue(method)
    do {
      val cur = queue.dequeue()
      handle(cur)
      val children = method.start.astChildren.l
      queue.addAll(children)
    } while (queue.nonEmpty)
    Iterator(diffGraph.build)
  }

  private def handle(cur: nodes.AstNode): Unit = {
    cur match {
      case method: nodes.Method => visitMethod(method)
      case _                    =>
    }
  }

  private def visitMethod(method: nodes.Method): Unit = {}

}
