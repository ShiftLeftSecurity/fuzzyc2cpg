package io.shiftleft.fuzzyc2cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.passes.IntervalKeyPool
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.semanticcpg.language._

class StubRemovalPassTests extends WordSpec with Matchers {

  "StubRemovalPass" should {
    "remove stub if non-stub with same signature exists" in StubRemovalPassFixture("""
        |int foo(int x);
        |int foo(int x) {
        | return 0;
        |}
        |""".stripMargin) { cpg =>
      cpg.method.name.l shouldBe List("foo")
      cpg.method.isStub.l shouldBe List()
      cpg.parameter.name.l shouldBe List("x")
      cpg.methodReturn.l.size shouldBe 1
    }

    "remove stub even if even parameter names differ" in StubRemovalPassFixture("""
        |int foo(int another_name);
        |int foo(int x) {
        | return 0;
        |}
        |""".stripMargin) { cpg =>
      cpg.method.name.l shouldBe List("foo")
      cpg.method.isStub.l shouldBe List()
      cpg.parameter.name.l shouldBe List("x")
      cpg.methodReturn.l.size shouldBe 1
    }

    "keep multiple implementations" in StubRemovalPassFixture("""
        |int foo(int x) { return x; }
        |int foo(int x) {
        | return 0;
        |}
        |""".stripMargin) { cpg =>
      cpg.method.name.l shouldBe List("foo", "foo")
    }

    "should remove all nodes of the stub" in StubRemovalPassFixture(
      """
        |int foo(int x);
        |int foo(int x) {
        | return 0;
        |}
        |""".stripMargin) {cpg =>
      cpg.parameter.size shouldBe 1
      cpg.methodReturn.size shouldBe 1
    }

  }

}

object StubRemovalPassFixture {
  def apply(file1Code: String)(f: Cpg => Unit): Unit = {
    File.usingTemporaryDirectory("fuzzyctest") { dir =>
      val file1 = (dir / "file1.c")
      file1.write(file1Code)
      val cpg = Cpg.emptyCpg
      val keyPool = new IntervalKeyPool(1001, 2000)
      val filenames = List(file1.path.toAbsolutePath.toString)
      val astCreator = new AstCreationPass(filenames, cpg, keyPool)
      astCreator.createAndApply()
      val cfgKeyPool = new IntervalKeyPool(2001, 3000)
      new CfgCreationPass(cpg, cfgKeyPool).createAndApply()
      new StubRemovalPass(cpg).createAndApply()
      f(cpg)
    }
  }
}
