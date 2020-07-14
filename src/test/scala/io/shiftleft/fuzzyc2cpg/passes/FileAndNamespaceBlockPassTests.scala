package io.shiftleft.fuzzyc2cpg.passes

import better.files.File
import io.shiftleft.codepropertygraph.Cpg
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.semanticcpg.language._

class FileAndNamespaceBlockPassTests extends WordSpec with Matchers {

  "FilePass" should {

    val cpg = Cpg.emptyCpg
    val filenames = List("foo/bar", "woo/moo")
    val expectedFilenameFields = filenames.map(f => File(f).path.toAbsolutePath.toString)
    new FileAndNamespaceBlockPass(filenames, cpg).createAndApply()

    "create one File node per file name" in {
      cpg.file.name.toSet shouldBe expectedFilenameFields.toSet
    }

    "create one NamespaceBlock per file" in {
      val expectedNamespaceFullNames = expectedFilenameFields.map(f => s"$f:<global>").toSet
      cpg.namespaceBlock.fullName.toSet shouldBe expectedNamespaceFullNames
    }

    "create SOURCE_FILE edges from File to NamespaceBlocks" in {
      cpg.file.l.size shouldBe 2
      cpg.file.l.flatMap(f => f.start.namespaceBlock.l.map((f, _))).size shouldBe 2
    }

  }
}
