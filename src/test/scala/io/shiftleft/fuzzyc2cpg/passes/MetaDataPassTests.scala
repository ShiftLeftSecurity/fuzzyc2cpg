package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.semanticcpg.language._

class MetaDataPassTests extends WordSpec with Matchers {

  "MetaDataPass" should {

    "create a metadata node with correct language" in {
      val cpg = Cpg.emptyCpg
      new MetaDataPass(cpg).createAndApply()
      cpg.metaData.language.l shouldBe List("C")
    }

    "create a '<global>' NamespaceBlock" in {
      val cpg = Cpg.emptyCpg
      new MetaDataPass(cpg).createAndApply()
      cpg.namespaceBlock.name.l shouldBe List("<global>")
    }

  }

}
