package io.shiftleft.fuzzyc2cpg.output

import io.shiftleft.proto.cpg.Cpg.CpgStruct
import java.io.IOException

/**
  * The CpgOutputModule describes the format of the CPG graph, e.g, TinkerGraph.
  */
trait CpgOutputModule {

  /**
    * Identifier for this output module which can be used to derive a name for
    * e.g. a resulting output file.
    */
  def setOutputIdentifier(identifier: String): Unit

  /**
    * Persists the individual CPG.
    *
    * @param cpg a CPG to be persisted (in memory or disk)
    */
  @throws[IOException]
  def persistCpg(cpg: CpgStruct.Builder): Unit
}
