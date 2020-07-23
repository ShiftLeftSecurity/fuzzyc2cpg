package io.shiftleft.fuzzyc2cpg

import org.scalatest.{Matchers, WordSpec}

import io.shiftleft.codepropertygraph.generated.{NodeKeys, NodeTypes}

class MethodDeclTest extends WordSpec with Matchers {

  private val fixture = CpgTestFixture("methoddecl")

  "MethodDeclTest" should {
    "omit the method declaration in presence of a definition" in {
      val result = fixture.V
        .hasLabel(NodeTypes.METHOD)
        .l

      result.size shouldBe 1
      val signature = result.head.property[String](NodeKeys.SIGNATURE.name).value
      signature shouldBe "int add (int,int)"
    }
  }
}
