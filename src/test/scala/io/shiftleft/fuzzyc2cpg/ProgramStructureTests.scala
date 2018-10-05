package io.shiftleft.fuzzyc2cpg

import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.scalatest.{Matchers, WordSpec}

class ProgramStructureTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("structure")
  val g = fixture.cpg.scalaGraph.traversal

  "Program structure of test project" should {

    "contain <global> namespace node" in {
      val namespaceBlock =
        g.V.hasLabel(NodeType.NAMESPACE_BLOCK.toString).headOption
      namespaceBlock.isDefined shouldBe true
      val name = namespaceBlock.get.property[String]("NAME").value
      name shouldBe "<global>"
    }

    "contain one file node" in {
      val fileNode = g.V.hasLabel(NodeType.FILE.toString).headOption
      fileNode.isDefined shouldBe true
      val fileName = fileNode.get.property[String]("NAME").value
      fileName shouldBe "src/test/resources/testcode/structure/structure.c"
    }

  }

}
