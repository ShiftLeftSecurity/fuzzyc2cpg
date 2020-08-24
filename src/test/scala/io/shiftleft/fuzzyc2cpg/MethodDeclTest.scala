package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.{NodeKeysOdb, NodeTypes}
import org.scalatest.{Matchers, WordSpec}

class MethodDeclTest extends WordSpec with Matchers {

  private val fixture = CpgTestFixture("methoddecl")

  "MethodDeclTest" should {
    "omit the method declaration in presence of a definition" in {
      val result = fixture.traversalSource
        .label(NodeTypes.METHOD)
        .l

      result.size shouldBe 1
      val signature = result.head.property2[String](NodeKeysOdb.SIGNATURE.name)
      signature shouldBe "int add (int,int)"
    }
  }
}
