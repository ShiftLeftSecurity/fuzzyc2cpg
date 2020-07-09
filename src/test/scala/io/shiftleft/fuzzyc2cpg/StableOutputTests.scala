package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.output.inmemory.OutputModuleFactory
import org.scalatest.{Matchers, WordSpec}

import scala.jdk.CollectionConverters._

class StableOutputTests extends WordSpec with Matchers {

  def createNodeStrings(): String = {
    val projectName = "stableid"
    val dirName = String.format("src/test/resources/testcode/%s", projectName)
    val inmemoryOutputFactory = new OutputModuleFactory()
    val fuzzyc2Cpg = new FuzzyC2Cpg(inmemoryOutputFactory)
    fuzzyc2Cpg.runAndOutput(Set(dirName), Set(".c", ".cc", ".cpp", ".h", ".hpp"))
    val cpg = inmemoryOutputFactory.getInternalGraph
    val nodes = cpg.graph.V().asScala.toList
    nodes.sortBy(_.id2()).map(x => x.label + ": " + x.propertyMap().asScala.toString).mkString("\n")
  }

  "Nodes in test graph" should {
    "should be exactly the same on ten consecutive runs" in {
      List
        .range(0, 10)
        .map { _ =>
          createNodeStrings()
        }
        .distinct
        .size shouldBe 1
    }
  }

}
