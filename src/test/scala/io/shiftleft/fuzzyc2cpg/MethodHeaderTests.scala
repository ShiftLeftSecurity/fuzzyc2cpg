package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.NodeTypes
import org.scalatest.{Matchers, WordSpec}

class MethodHeaderTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodheader")
  val g = fixture.cpg.scalaGraph.traversal

  "Method header test project" should {

    "contain one method node with NAME `foo`" in {
      val methods = g.V.hasLabel(NodeTypes.METHOD).l
      methods.size shouldBe 1
      val method = methods.head
      method.property[String]("NAME").value shouldBe "foo"
    }

    "contain one method node with FULL_NAME `foo`" in {
      val methods = g.V.hasLabel(NodeTypes.METHOD).l
      methods.size shouldBe 1
      val method = methods.head
      method.property[String]("FULL_NAME").value shouldBe "foo"
    }

    "contain two parameter nodes" in {
      val parameters = g.V.hasLabel(NodeTypes.METHOD_PARAMETER_IN).l.toSet
      parameters.map(_.property[String]("NAME").value) shouldBe Set("x", "y")
    }

  }

}
