package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.fuzzyc2cpg.Defines
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.semanticcpg.language._

import scala.jdk.CollectionConverters._

class CMetaDataPassTests extends WordSpec with Matchers {

  "MetaDataPass" should {
    val cpg = Cpg.emptyCpg
    new CMetaDataPass(cpg).createAndApply()

    "create exactly three nodes" in {
      cpg.graph.V.asScala.size shouldBe 3
    }

    "create one edge" in {
      cpg.graph.E.asScala.size shouldBe 1
    }

    "create a metadata node with correct language" in {
      cpg.metaData.language.l shouldBe List("C")
    }

    "create a '<global>' NamespaceBlock" in {
      cpg.namespaceBlock.name.l shouldBe List(Defines.globalNamespaceName)
    }

    "connect '<global>' with a file node" in {
      cpg.namespaceBlock.name("<global>").file.size shouldBe 1
    }

  }
}
