package io.shiftleft.fuzzyc2cpg

import org.scalatest.{Matchers, WordSpec}

class MethodHeaderTests extends WordSpec with Matchers {
  val fixture = CpgTestFixture("methodheader")
  val g = fixture.cpg.scalaGraph.traversal
}
