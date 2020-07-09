package io.shiftleft.fuzzyc2cpg.output.inmemory

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.cpgloading.ProtoCpgLoader
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule
import io.shiftleft.passes.DiffGraph
import io.shiftleft.passes.KeyPool
import java.io.IOException

import overflowdb.OdbConfig
import io.shiftleft.proto.cpg.Cpg.CpgStruct

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class OutputModule extends CpgOutputModule {
  var cpgBuilders: mutable.ListBuffer[CpgStruct.Builder] = ListBuffer[CpgStruct.Builder]()
  private var cpg: Cpg = _

  def getInternalGraph: Cpg = cpg

  override def persistCpg(builder: CpgStruct.Builder, identifier: String): Unit = {
    cpgBuilders synchronized {
      cpgBuilders.addOne(builder)
    }
  }

  @throws[IOException]
  override def persistCpg(diffGraph: DiffGraph, keyPool: KeyPool, identifier: String): Unit = {
    // TODO
  }

  def persist(): Unit = {
    val mergedBuilder = CpgStruct.newBuilder
    cpgBuilders.foreach { builder: CpgStruct.Builder =>
      mergedBuilder.mergeFrom(builder.build)
    }
    cpg = ProtoCpgLoader.loadFromListOfProtos(List(mergedBuilder.build), OdbConfig.withoutOverflow)
  }
}
