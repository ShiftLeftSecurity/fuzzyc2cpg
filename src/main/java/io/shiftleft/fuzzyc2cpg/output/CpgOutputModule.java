package io.shiftleft.fuzzyc2cpg.output;

import io.shiftleft.proto.cpg.Cpg.CpgStruct;

import java.io.IOException;

/**
 * The CpgOutputModule describes the format of the CPG graph, e.g, TinkerGraph.
 */
public interface CpgOutputModule {

  /**
   * Set the className and methodName which can be used by the output module
   * to organize or structure the output.
   */

  void setClassAndMethodName(String className, String methodName);

  /**
   * Persists the individual CPG.
   *
   * @param cpg a CPG to be persisted (in memory or disk)
   */
  void persistCpg(CpgStruct.Builder cpg) throws IOException;

}
