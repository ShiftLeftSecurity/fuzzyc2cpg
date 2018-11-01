package io.shiftleft.fuzzyc2cpg

import org.scalatest.{Matchers, WordSpec}

class TestTest extends WordSpec with Matchers {
  val fixture = CpgTestFixture("testtest")

  "Tests" should {
    "load graphs" in {
      fixture.V.l.size should be  > 0
    }
  }

}
