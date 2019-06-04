package io.shiftleft.fuzzyc2cpg.output;

import io.shiftleft.proto.cpg.Cpg.CpgStruct;

import java.io.IOException;

/**
 * The CpgOutputModule describes the format of the CPG graph, e.g, TinkerGraph.
 */
public interface CpgOutputModule {

  /**
   * Identifier for this output module which can be used to derive a name for
   * e.g. a resulting output file.
   */

  void setOutputIdentifier(String identifier);

  /**
   * We allow CPGs to be stored in sub directories. This makes
   * it possible to encode information about what can be found
   * in the CPG as part of the path name.
   * */
  void setOutputSubDir(String subdir);

  /**
   * Persists the individual CPG.
   *
   * @param cpg a CPG to be persisted (in memory or disk)
   */
  void persistCpg(CpgStruct.Builder cpg) throws IOException;

}
