package io.shiftleft.fuzzyc2cpg.output

import io.shiftleft.proto.cpg.Cpg.CpgStruct
import java.io.IOException

import io.shiftleft.passes.{DiffGraph, KeyPool}

/**
  * The CpgOutputModule describes the format of the CPG graph, e.g, TinkerGraph.
  */
trait CpgOutputModule {

  /**
    * Persists the individual CPG.
    *
    * @param cpg a CPG to be persisted (in memory or disk)
    */
  @throws[IOException]
  def persistCpg(cpg: CpgStruct.Builder): Unit

  @throws[IOException]
  def persistCpg(diffGraph: DiffGraph, keyPool: KeyPool): Unit

}
