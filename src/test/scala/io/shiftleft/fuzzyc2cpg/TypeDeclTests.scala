package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.NodeKeys
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.scalatest.{Matchers, WordSpec}

class TypeDeclTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("typedecl")

  "Type decl test project" should {
    "contain one internal type decl node for Foo" in {
      val typeDeclNodes = fixture.V.hasLabel(NodeType.TYPE_DECL.toString)
        .has(NodeKeys.NAME -> "Foo")
        .l
      typeDeclNodes.size shouldBe 1
      typeDeclNodes.head.value[Boolean](NodeKeys.IS_EXTERNAL.name) shouldBe false
    }

    "contain three member nodes" in {
      fixture.V.hasLabel(NodeType.MEMBER.toString).l.size shouldBe 3
    }

    "contain edges from Foo to three members" in {
      val members = fixture.V.hasLabel(NodeType.TYPE_DECL.toString)
        .out("AST").hasLabel(NodeType.MEMBER.toString).l
      members.size shouldBe 3
    }

    "contain correct code fields for all members" in {
      val members = fixture.V.hasLabel(NodeType.MEMBER.toString).l
      members.map(_.value[String]("CODE")).toSet shouldBe
        Set("x", "y", "*foo")
    }

  }
}
