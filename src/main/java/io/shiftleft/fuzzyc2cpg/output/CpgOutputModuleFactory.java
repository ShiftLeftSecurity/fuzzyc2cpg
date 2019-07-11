package io.shiftleft.fuzzyc2cpg.output;

import java.io.IOException;

/**
 * Output module factory.
 */
public interface CpgOutputModuleFactory {

  /**
   * A CpgOutputModule associated with the given factory.
   *
   * @return a singleton output module
   */
  CpgOutputModule create() throws IOException;

  /**
   * A finalization method that potentially combines all CPGs added to any of the
   * output module created through this factory.
   */
  void persist() throws IOException;
}
