package io.shiftleft.fuzzyc2cpg

import gremlin.scala.Vertex
import io.shiftleft.codepropertygraph.generated.{NodeKeys, NodeTypes}
import org.scalatest.Matchers

trait TraversalUtils extends Matchers {
  val fixture: CpgTestFixture

  def getMethod(name: String): List[Vertex] = {
    val result = fixture.V
      .hasLabel(NodeTypes.METHOD)
      .has(NodeKeys.NAME -> name)
      .l

    result.size shouldBe 1
    result
  }

}
