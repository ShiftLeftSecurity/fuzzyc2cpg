package io.shiftleft.fuzzyc2cpg.output;

import java.io.IOException;

/**
 * Output module factory.
 * @param <T> The internal type of the graph
 */
public interface CpgOutputModuleFactory<T> {

  /**
   * A CpgOutputModule associated with the given factory.
   *
   * @return a singleton output module
   */
  CpgOutputModule create() throws IOException;

  /**
   * An internal representation of the graph.
   *
   * @return the internally constructed graph
   */
  T getInternalGraph();

  /**
   * A finalization method that potentially combines all CPGs added to any of the
   * output module created through this factory.
   */
  void persist() throws IOException;
}
