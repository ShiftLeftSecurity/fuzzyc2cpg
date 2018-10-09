package io.shiftleft.fuzzyc2cpg

import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.scalatest.{Matchers, WordSpec}

class TypeDeclTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("typedecl")
  val g = fixture.cpg.scalaGraph.traversal

  "Type decl test project" should {
    "contain one type decl node for Foo" in {
      val typeDeclNodes = g.V.hasLabel(NodeType.TYPE_DECL.toString).l
      typeDeclNodes.size shouldBe 1
      typeDeclNodes.head.property[String]("NAME")
        .value shouldBe "Foo"
    }

    "contain three member nodes" in {
      g.V.hasLabel(NodeType.MEMBER.toString).l.size shouldBe 3
    }

    "contain edges from Foo to three members" in {
      val members = g.V.hasLabel(NodeType.TYPE_DECL.toString)
        .out("AST").hasLabel(NodeType.MEMBER.toString).l
      members.size shouldBe 3
    }

    "contain correct code fields for all members" in {
      val members = g.V.hasLabel(NodeType.MEMBER.toString).l
      members.map(_.value[String]("CODE")).foreach(println(_))
    }

  }
}
