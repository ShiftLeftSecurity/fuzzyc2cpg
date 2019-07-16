package io.shiftleft.fuzzyc2cpg.cfg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
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

  private class GraphAdapter extends CfgAdapter[CfgNode] {
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
      pairs.map {
        case (code, cfgEdgeType) =>
          CfgNodeEdgePair(codeToCpgNode(code), cfgEdgeType)
      }.toSet
    }

    def succOf(code: String): Set[CfgNodeEdgePair] = {
      codeToCpgNode(code).successors
    }
  }

  "Cfg" should {
    "be correct for decl statement with assignment" in
      new Fixture("int x = 1;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x = 1", AlwaysEdge))
        succOf("x = 1") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for nested expression" in
      new Fixture("x = y + 1;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("y + 1", AlwaysEdge))
        succOf("y + 1") shouldBe expected(("x = y + 1", AlwaysEdge))
        succOf("x = y + 1") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for return statement" in
      new Fixture("return x;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("return x;", AlwaysEdge))
        succOf("return x;") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for consecutive return statements" in
      new Fixture("return x; return y;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("return x;", AlwaysEdge))
        succOf("y") shouldBe expected(("return y;", AlwaysEdge))
        succOf("return x;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("return y;") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for void return statement" in
      new Fixture("return;") {
        succOf("ENTRY") shouldBe expected(("return;", AlwaysEdge))
        succOf("return;") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for call expression" in
      new Fixture("foo(a + 1, b);") {
        succOf("ENTRY") shouldBe expected(("a", AlwaysEdge))
        succOf("a") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("a + 1", AlwaysEdge))
        succOf("a + 1") shouldBe expected(("b", AlwaysEdge))
        succOf("b") shouldBe expected(("foo(a + 1, b)", AlwaysEdge))
        succOf("foo(a + 1, b)") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for unary expression '+'" in
      new Fixture("+x;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("+x", AlwaysEdge))
        succOf("+x") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for unary expression '++'" in
      new Fixture("++x;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("++x", AlwaysEdge))
        succOf("++x") shouldBe expected(("EXIT", AlwaysEdge))
      }

    // TODO This is wrong but intention, see comment on
    // visitor function for ConditionExpression.
    "be correct for conditional expression" in
      new Fixture("x ? y : z;") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("z", AlwaysEdge))
        succOf("z") shouldBe expected(("x ? y : z", AlwaysEdge))
        succOf("x ? y : z") shouldBe expected(("EXIT", AlwaysEdge))
      }
  }

  "Cfg for while-loop" should {
    "be correct" in
      new Fixture("while (x < 1) { y = 2; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
        succOf("x < 1") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
        succOf("y") shouldBe expected(("2", AlwaysEdge))
        succOf("2") shouldBe expected(("y = 2", AlwaysEdge))
        succOf("y = 2") shouldBe expected(("x", AlwaysEdge))
      }

    "be correct with break" in
      new Fixture("while (x < 1) { break; y; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
        succOf("x < 1") shouldBe expected(("break;", TrueEdge), ("EXIT", FalseEdge))
        succOf("break;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("y") shouldBe expected(("x", AlwaysEdge))
      }

    "be correct with continue" in
      new Fixture("while (x < 1) { continue; y; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
        succOf("x < 1") shouldBe expected(("continue;", TrueEdge), ("EXIT", FalseEdge))
        succOf("continue;") shouldBe expected(("x", AlwaysEdge))
        succOf("y") shouldBe expected(("x", AlwaysEdge))
      }

    "be correct with nested while-loop" in
      new Fixture("while (x) { while (y) { z; }}") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
        succOf("y") shouldBe expected(("z", TrueEdge), ("x", FalseEdge))
        succOf("z") shouldBe expected(("y", AlwaysEdge))
      }
  }

  "Cfg for do-while-loop" should {
    "be correct" in
      new Fixture("do { y = 2; } while (x < 1);") {
        succOf("ENTRY") shouldBe expected(("y", AlwaysEdge))
        succOf("ENTRY") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("2", AlwaysEdge))
        succOf("2") shouldBe expected(("y = 2", AlwaysEdge))
        succOf("y = 2") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
        succOf("x < 1") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
      }

    "be correct with break" in
      new Fixture("do { break; y; } while (x < 1);") {
        succOf("ENTRY") shouldBe expected(("break;", AlwaysEdge))
        succOf("break;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("y") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
        succOf("x < 1") shouldBe expected(("break;", TrueEdge), ("EXIT", FalseEdge))
      }

    "be correct with continue" in
      new Fixture("do { continue; y; } while (x < 1);") {
        succOf("ENTRY") shouldBe expected(("continue;", AlwaysEdge))
        succOf("continue;") shouldBe expected(("x", AlwaysEdge))
        succOf("y") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("x < 1", AlwaysEdge))
        succOf("x < 1") shouldBe expected(("continue;", TrueEdge), ("EXIT", FalseEdge))
      }

    "be correct with nested do-while-loop" in
      new Fixture("do { do { x; } while (y); } while (z);") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("x", TrueEdge), ("z", FalseEdge))
        succOf("z") shouldBe expected(("x", TrueEdge), ("EXIT", FalseEdge))
      }
  }

  "Cfg for for-loop" should {
    "be correct" in
      new Fixture("for (x = 0; y < 1; z += 2) { a = 3; }") {
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

    "be correct with break" in
      new Fixture("for (x = 0; y < 1; z += 2) { break; a = 3; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("0", AlwaysEdge))
        succOf("x = 0") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("y < 1", AlwaysEdge))
        succOf("y < 1") shouldBe expected(("break;", TrueEdge), ("EXIT", FalseEdge))
        succOf("break;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("a") shouldBe expected(("3", AlwaysEdge))
        succOf("3") shouldBe expected(("a = 3", AlwaysEdge))
        succOf("a = 3") shouldBe expected(("z", AlwaysEdge))
        succOf("z") shouldBe expected(("2", AlwaysEdge))
        succOf("2") shouldBe expected(("z += 2", AlwaysEdge))
        succOf("z += 2") shouldBe expected(("y", AlwaysEdge))
      }

    "be correct with continue" in
      new Fixture("for (x = 0; y < 1; z += 2) { continue; a = 3; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("0", AlwaysEdge))
        succOf("0") shouldBe expected(("x = 0", AlwaysEdge))
        succOf("x = 0") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("y < 1", AlwaysEdge))
        succOf("y < 1") shouldBe expected(("continue;", TrueEdge), ("EXIT", FalseEdge))
        succOf("continue;") shouldBe expected(("z", AlwaysEdge))
        succOf("a") shouldBe expected(("3", AlwaysEdge))
        succOf("3") shouldBe expected(("a = 3", AlwaysEdge))
        succOf("a = 3") shouldBe expected(("z", AlwaysEdge))
        succOf("z") shouldBe expected(("2", AlwaysEdge))
        succOf("2") shouldBe expected(("z += 2", AlwaysEdge))
        succOf("z += 2") shouldBe expected(("y", AlwaysEdge))
      }

    "be correct with nested for-loop" in
      new Fixture("for (x; y; z) { for (a; b; c) { u; } }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", AlwaysEdge))
        succOf("y") shouldBe expected(("a", TrueEdge), ("EXIT", FalseEdge))
        succOf("z") shouldBe expected(("y", AlwaysEdge))
        succOf("a") shouldBe expected(("b", AlwaysEdge))
        succOf("b") shouldBe expected(("u", TrueEdge), ("z", FalseEdge))
        succOf("c") shouldBe expected(("b", AlwaysEdge))
        succOf("u") shouldBe expected(("c", AlwaysEdge))
      }

    "be correct with empty condition" in
      new Fixture("for (;;) { a = 1; }") {
        succOf("ENTRY") shouldBe expected(("a", AlwaysEdge))
        succOf("a") shouldBe expected(("1", AlwaysEdge))
        succOf("1") shouldBe expected(("a = 1", AlwaysEdge))
        succOf("a = 1") shouldBe expected(("a", AlwaysEdge))
      }

    "be correct with empty condition with break" in
      new Fixture("for (;;) { break; }") {
        succOf("ENTRY") shouldBe expected(("break;", AlwaysEdge))
        succOf("break;") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with empty condition with continue" in
      new Fixture("for (;;) { continue ; }") {
        succOf("ENTRY") shouldBe expected(("continue ;", AlwaysEdge))
        succOf("continue ;") shouldBe expected(("continue ;", AlwaysEdge))
      }

    "be correct with empty condition with nested empty for-loop" in
      new Fixture("for (;;) { for (;;) { x; } }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("x", AlwaysEdge))
      }

    "be correct with empty condition with empty block" in
      new Fixture("for (;;) ;") {
        succOf("ENTRY") shouldBe expected()
      }

    "be correct when empty for-loop is skipped" in
      new Fixture("for (;;) {}; return;") {
        succOf("ENTRY") shouldBe expected()
        succOf("return;") shouldBe expected(("EXIT", AlwaysEdge))
      }
  }

  "Cfg for goto" should {
    "be correct for single label" in
      new Fixture("x; goto l1; y; l1:") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("goto l1;", AlwaysEdge))
        succOf("goto l1;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for multiple labels" in
      new Fixture("x;goto l1; l2: y; l1:") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("goto l1;", AlwaysEdge))
        succOf("goto l1;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for multiple labels on same spot" in
      new Fixture("x;goto l2;y;l1:l2:") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("goto l2;", AlwaysEdge))
        succOf("goto l2;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }
  }

  "Cfg for switch" should {
    "be correct with one case" in
      new Fixture("switch (x) { case 1: y; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge), ("EXIT", CaseEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with multiple cases" in
      new Fixture("switch (x) { case 1: y; case 2: z;}") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge), ("z", CaseEdge), ("EXIT", CaseEdge))
        succOf("y") shouldBe expected(("z", AlwaysEdge))
        succOf("z") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with multiple cases on same spot" in
      new Fixture("switch (x) { case 1: case 2: y; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge), ("EXIT", CaseEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with multiple cases and multiple cases on same spot" in
      new Fixture("switch (x) { case 1: case 2: y; case 3: z;}") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge), ("z", CaseEdge), ("EXIT", CaseEdge))
        succOf("y") shouldBe expected(("z", AlwaysEdge))
        succOf("z") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with default case" in
      new Fixture("switch (x) { default: y; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for case and default combined" in
      new Fixture("switch (x) { case 1: y; break; default: z;}") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge), ("z", CaseEdge))
        succOf("y") shouldBe expected(("break;", AlwaysEdge))
        succOf("break;") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("z") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct for nested switch" in
      new Fixture("switch (x) { default: switch(y) { default: z; } }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", CaseEdge))
        succOf("y") shouldBe expected(("z", CaseEdge))
        succOf("z") shouldBe expected(("EXIT", AlwaysEdge))
      }
  }

  "Cfg for if" should {
    "be correct" in
      new Fixture("if (x) { y; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with else block" in
      new Fixture("if (x) { y; } else { z; }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", TrueEdge), ("z", FalseEdge))
        succOf("y") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("z") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with nested if" in
      new Fixture("if (x) { if (y) { z; } }") {
        succOf("ENTRY") shouldBe expected(("x", AlwaysEdge))
        succOf("x") shouldBe expected(("y", TrueEdge), ("EXIT", FalseEdge))
        succOf("y") shouldBe expected(("z", TrueEdge), ("EXIT", FalseEdge))
        succOf("z") shouldBe expected(("EXIT", AlwaysEdge))
      }

    "be correct with else if chain" in
      new Fixture("if (a) { b; } else if (c) { d;} else { e; }") {
        succOf("ENTRY") shouldBe expected(("a", AlwaysEdge))
        succOf("a") shouldBe expected(("b", TrueEdge), ("c", FalseEdge))
        succOf("b") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("c") shouldBe expected(("d", TrueEdge), ("e", FalseEdge))
        succOf("d") shouldBe expected(("EXIT", AlwaysEdge))
        succOf("e") shouldBe expected(("EXIT", AlwaysEdge))
      }
  }
}
