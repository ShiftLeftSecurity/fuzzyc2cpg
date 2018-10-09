package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.{NodeKeys, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodHeaderTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodheader")
  val g = fixture.cpg.scalaGraph.traversal

  "Method header test project" should {

    "contain one method node with NAME `foo`" in {
      val methods = g.V.hasLabel(NodeTypes.METHOD).value(NodeKeys.NAME).l
      methods.size shouldBe 1
      methods.head shouldBe "foo"
    }

    "contain one method node with FULL_NAME `foo`" in {
      val methods = g.V.hasLabel(NodeTypes.METHOD).value(NodeKeys.FULL_NAME).l
      val method = methods.head
      method shouldBe "foo"
    }

    "contain two parameter nodes" in {
      val parameters = g.V.hasLabel(NodeTypes.METHOD_PARAMETER_IN)
        .value(NodeKeys.NAME).l.toSet
      parameters shouldBe Set("x", "y")
    }

    "contain one METHOD_RETURN node with correct code field" in {
      val returns = g.V.hasLabel(NodeTypes.METHOD_RETURN)
        .value(NodeKeys.TYPE_FULL_NAME)
        .l
      returns.size shouldBe 1
      returns.head shouldBe "int"
    }

  }

}
