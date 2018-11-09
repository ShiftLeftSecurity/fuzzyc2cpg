package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.cfgnew._
import io.shiftleft.fuzzyc2cpg.parsetreetoast.FunctionContentTestUtil
import org.scalatest.{Matchers, WordSpec}

class AstToCfgTests extends WordSpec with Matchers {
  private case class CfgNodeEdgePair(cfgNode: CfgNode, cfgEdgeType: CfgEdgeType) {
    override def toString: String = {
      s"${cfgEdgeType.getClass.getSimpleName} ==> ${cfgNode.code}"
    }
  }
  private class CfgNode(val code: String, var successors: Set[CfgNodeEdgePair] = Set()) {
    override def toString: String = {
      s"$code === ${successors.mkString(", ")}"
    }
  }

  private class GraphAdapter extends DestinationGraphAdapter[CfgNode] {
    private var mapping = Map[AstNode, CfgNode]()
    var codeToCfgNode = Map[String, CfgNode]()

    override def mapNode(astNode: AstNode): CfgNode = {
      if (mapping.contains(astNode)) {
        mapping(astNode)
      } else {
        val cfgNode = new CfgNode(astNode.getEscapedCodeStr)
        mapping += astNode -> cfgNode
        codeToCfgNode += astNode.getEscapedCodeStr -> cfgNode
        cfgNode
      }
    }

    override def newCfgEdge(dstNode: CfgNode, srcNode: CfgNode, cfgEdgeType: CfgEdgeType): Unit = {
      if (srcNode.successors.exists(_.cfgNode == dstNode)) {
        throw new RuntimeException("Found duplicate edge.")
      }
      srcNode.successors = srcNode.successors + CfgNodeEdgePair(dstNode, cfgEdgeType)
    }
  }

  private def createAstFromCode(code: String): AstNode = {
    FunctionContentTestUtil.parseAndWalk(code)
  }

  private class Fixture(code: String) {
    private val astRoot = FunctionContentTestUtil.parseAndWalk(code)
    private val entry = new CfgNode("ENTRY")
    private val exit = new CfgNode("EXIT")

    private val adapter = new GraphAdapter()
    private val astToCfgConverter = new AstToCfgConverter(entry, exit, adapter)
    astToCfgConverter.convert(astRoot)

    private var codeToCpgNode = adapter.codeToCfgNode
    codeToCpgNode += entry.code -> entry
    codeToCpgNode += exit.code -> exit


    def expected(pairs: (String, CfgEdgeType)*): Set[CfgNodeEdgePair] = {
      pairs.map { case (code, cfgEdgeType) =>
        CfgNodeEdgePair(codeToCpgNode(code), cfgEdgeType)
      }.toSet
    }

    def succOf(code: String): Set[CfgNodeEdgePair] = {
      codeToCpgNode(code).successors
    }
  }

  "Cfg" should {
    "correct for nested expression" in new Fixture("x = y + 1;") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("y", AlwaysEdge))
      succOf("y") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("y + 1", AlwaysEdge))
      succOf("y + 1") shouldBe expected(("x = y + 1", AlwaysEdge))
      succOf("x = y + 1") shouldBe expected(("EXIT", AlwaysEdge))
    }
  }

  "Cfg for while-loop" should {
    "be correct" in new Fixture("while (x < 1) { y = 2; }") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
      succOf("x < 1") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
      succOf("y") shouldBe expected(("2", AlwaysEdge))
      succOf("2") shouldBe expected(("y = 2", AlwaysEdge))
      succOf("y = 2") shouldBe expected(("x", AlwaysEdge))
    }

    "be correct with break" in new Fixture("while (x < 1) { break; y; }") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
      succOf("x < 1") shouldBe expected(("break ;", TrueEdge), ("EXIT", FalseEdge))
      succOf("break ;") shouldBe expected(("EXIT", AlwaysEdge))
      succOf("y") shouldBe expected(("x", AlwaysEdge))
    }

    "be correct with continue" in new Fixture("while (x < 1) { continue; y; }") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
      succOf("x < 1") shouldBe expected(("continue ;", TrueEdge), ("EXIT", FalseEdge))
      succOf("continue ;") shouldBe expected(("x", AlwaysEdge))
      succOf("y") shouldBe expected(("x", AlwaysEdge))
    }
  }

  "Cfg for do-while-loop" should {
    "be correct" in new Fixture("do { y = 2; } while (x < 1);") {
      succOf("ENTRY") shouldBe expected(("y", AlwaysEdge))
      succOf("y") shouldBe expected(("2", AlwaysEdge))
      succOf("2") shouldBe expected(("y = 2", AlwaysEdge))
      succOf("y = 2") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
      succOf("x < 1") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
    }

    "be correct with break" in new Fixture("do { break; y; } while (x < 1);") {
      succOf("ENTRY") shouldBe expected(("break ;", AlwaysEdge))
      succOf("break ;") shouldBe expected(("EXIT", AlwaysEdge))
      succOf("y") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
      succOf("x < 1") shouldBe expected(("break ;", TrueEdge), ("EXIT", FalseEdge))
    }

    "be correct with continue" in new Fixture("do { continue; y; } while (x < 1);") {
      succOf("ENTRY") shouldBe expected(("continue ;", AlwaysEdge))
      succOf("continue ;") shouldBe expected(("x", AlwaysEdge))
      succOf("y") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
      succOf("x < 1") shouldBe expected(("continue ;", TrueEdge), ("EXIT", FalseEdge))
    }
  }

  "Cfg for for-loop" should {
    "be correct" in new Fixture("for (x = 0; y < 1; z += 2) { a = 3; }") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("0", AlwaysEdge))
      succOf("0") shouldBe expected(("x = 0", AlwaysEdge))
      succOf("x = 0") shouldBe expected(("y", AlwaysEdge))
      succOf("y") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("y < 1", AlwaysEdge))
      succOf("y < 1") shouldBe expected(("a", TrueEdge), ("EXIT", FalseEdge))
      succOf("a") shouldBe expected(("3", AlwaysEdge))
      succOf("3") shouldBe expected(("a = 3", AlwaysEdge))
      succOf("a = 3") shouldBe expected(("z", AlwaysEdge))
      succOf("z") shouldBe expected(("2", AlwaysEdge))
      succOf("2") shouldBe expected(("z += 2", AlwaysEdge))
      succOf("z += 2") shouldBe expected(("y", AlwaysEdge))
    }

    "be correct with break" in new Fixture("for (x = 0; y < 1; z += 2) { break; a = 3; }") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("0", AlwaysEdge))
      succOf("x = 0") shouldBe expected(("y", AlwaysEdge))
      succOf("y") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("y < 1", AlwaysEdge))
      succOf("y < 1") shouldBe expected(("break ;", TrueEdge), ("EXIT", FalseEdge))
      succOf("break ;") shouldBe expected(("EXIT", AlwaysEdge))
      succOf("a") shouldBe expected(("3", AlwaysEdge))
      succOf("3") shouldBe expected(("a = 3", AlwaysEdge))
      succOf("a = 3") shouldBe expected(("z", AlwaysEdge))
      succOf("z") shouldBe expected(("2", AlwaysEdge))
      succOf("2") shouldBe expected(("z += 2", AlwaysEdge))
      succOf("z += 2") shouldBe expected(("y", AlwaysEdge))
    }

    "be correct with continue" in new Fixture("for (x = 0; y < 1; z += 2) { continue; a = 3; }") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("0", AlwaysEdge))
      succOf("0") shouldBe expected(("x = 0", AlwaysEdge))
      succOf("x = 0") shouldBe expected(("y", AlwaysEdge))
      succOf("y") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("y < 1", AlwaysEdge))
      succOf("y < 1") shouldBe expected(("continue ;", TrueEdge), ("EXIT", FalseEdge))
      succOf("continue ;") shouldBe expected(("z", AlwaysEdge))
      succOf("a") shouldBe expected(("3", AlwaysEdge))
      succOf("3") shouldBe expected(("a = 3", AlwaysEdge))
      succOf("a = 3") shouldBe expected(("z", AlwaysEdge))
      succOf("z") shouldBe expected(("2", AlwaysEdge))
      succOf("2") shouldBe expected(("z += 2", AlwaysEdge))
      succOf("z += 2") shouldBe expected(("y", AlwaysEdge))
    }

    "be correct for with empty condition" in new Fixture("for (;;) { a = 1; }") {
      succOf("ENTRY") shouldBe expected(("a", AlwaysEdge))
      succOf("a") shouldBe expected(("1", AlwaysEdge))
      succOf("1") shouldBe expected(("a = 1", AlwaysEdge))
      succOf("a = 1") shouldBe expected(("a", AlwaysEdge))
    }

    "be correct for with empty condition with break" in
      new Fixture("for (;;) { break; }") {
      succOf("ENTRY") shouldBe expected(("break ;", AlwaysEdge))
      succOf("break ;") shouldBe expected(("EXIT", AlwaysEdge))
    }

    "be correct for with empty condition with continue" in
      new Fixture("for (;;) { continue ; }") {
        succOf("ENTRY") shouldBe expected(("continue ;", AlwaysEdge))
        succOf("continue ;") shouldBe expected(("continue ;", AlwaysEdge))
      }
  }

  "Cfg for goto" should {
    "be correct for single label" in new Fixture("x; goto l1; y; l1:") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("goto l1 ;", AlwaysEdge))
      succOf("goto l1 ;") shouldBe expected(("EXIT", AlwaysEdge))
      succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
    }

    "be correct for multiple labels" in new Fixture("x; goto l1; l2: y; l1:") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("goto l1 ;", AlwaysEdge))
      succOf("goto l1 ;") shouldBe expected(("EXIT", AlwaysEdge))
      succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
    }

    "be correct for multiple labels on same spot" in new Fixture("x; goto l2; y; l1:l2:") {
      succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
      succOf("x") shouldBe expected(("goto l2 ;", AlwaysEdge))
      succOf("goto l2 ;") shouldBe expected(("EXIT", AlwaysEdge))
      succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
    }
  }

}
