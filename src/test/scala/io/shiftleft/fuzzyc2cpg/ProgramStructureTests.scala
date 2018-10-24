package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.NodeKeys
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.scalatest.{Matchers, WordSpec}

class ProgramStructureTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("structure")
  val g = fixture.cpg.scalaGraph.traversal

  "Program structure of test project" should {

    "contain <global> namespace block node" in {
      val namespaceBlocks =
        g.V.hasLabel(NodeType.NAMESPACE_BLOCK.toString)
        .value(NodeKeys.NAME).l

      namespaceBlocks.size shouldBe 1
      namespaceBlocks.head shouldBe "<global>"
    }

    "contain one file node" in {
      val fileName = g.V.hasLabel(NodeType.FILE.toString)
        .value(NodeKeys.NAME)
        .headOption
      fileName.isDefined shouldBe true
      fileName.head shouldBe "src/test/resources/testcode/structure/structure.c"
    }

    "contain AST edge from file node to namespace block" in {
      val nodes = g.V.hasLabel(NodeType.FILE.toString)
        .out("AST")
        .hasLabel(NodeType.NAMESPACE_BLOCK.toString).l
      nodes.size shouldBe 1
    }

    "contain type-decl node" in {
      val nodes = g.V.hasLabel(NodeType.TYPE_DECL.toString).l
      nodes.size shouldBe 1
    }


  }

}
