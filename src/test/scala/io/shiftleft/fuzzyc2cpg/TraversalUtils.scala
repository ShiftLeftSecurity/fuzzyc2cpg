package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.{NodeKeysOdb, NodeTypes}
import org.scalatest.Matchers
import overflowdb.Node

trait TraversalUtils extends Matchers {
  val fixture: CpgTestFixture

  def getMethod(name: String): List[Node] = {
    val result =
      fixture
        .traversalSource
        .label(NodeTypes.METHOD)
        .has(NodeKeysOdb.NAME -> name)
        .l

    result.size shouldBe 1
    result
  }

}
