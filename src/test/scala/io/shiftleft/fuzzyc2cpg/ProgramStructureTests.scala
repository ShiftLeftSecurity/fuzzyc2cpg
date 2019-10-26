package io.shiftleft.fuzzyc2cpg

import io.shiftleft.codepropertygraph.generated.NodeKeys
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.scalatest.{Matchers, WordSpec}

class ProgramStructureTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("structure")

  "Program structure of test project" should {

    "contain <global> namespace block node" in {
      val namespaceBlocks =
        fixture.V
          .hasLabel(NodeType.NAMESPACE_BLOCK.toString)
          .has(NodeKeys.FULL_NAME -> Defines.globalNamespaceName)
          .l

      namespaceBlocks.size shouldBe 1
    }

    "contain one file node" in {
      val fileName = fixture.V
        .hasLabel(NodeType.FILE.toString)
        .value(NodeKeys.NAME)
        .headOption
      fileName.isDefined shouldBe true
      fileName.head should not contain ".."
      fileName.head should not be "src/test/resources/testcode/structure/structure.c"
      fileName.head should endWith("src/test/resources/testcode/structure/structure.c")
    }

    "contain AST edge from file node to namespace block" in {
      val nodes = fixture.V
        .hasLabel(NodeType.FILE.toString)
        .out("AST")
        .hasLabel(NodeType.NAMESPACE_BLOCK.toString)
        .l
      nodes.size shouldBe 1
    }

    "contain type-decl node" in {
      val nodes = fixture.V.hasLabel(NodeType.TYPE_DECL.toString).l
      nodes.size should be > 0
    }

  }

}
