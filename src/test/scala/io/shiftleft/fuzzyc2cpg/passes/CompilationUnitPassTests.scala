package io.shiftleft.fuzzyc2cpg.passes

import io.shiftleft.codepropertygraph.Cpg
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.passes.DiffGraph

class CompilationUnitPassTests extends WordSpec with Matchers {

  "CompilationUnitPass" should {

    val cpg = Cpg.emptyCpg
    implicit val diffGraph: DiffGraph.Builder = DiffGraph.newBuilder
    val filenames = List("foo/bar", "woo/moo")
    new FileAndNamespaceBlockPass(filenames, cpg).createAndApply()

  }

}
