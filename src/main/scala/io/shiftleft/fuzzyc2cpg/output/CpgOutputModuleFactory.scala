package io.shiftleft.fuzzyc2cpg.output

import java.io.IOException

/**
  * Output module factory.
  */
trait CpgOutputModuleFactory {

  /**
    * A CpgOutputModule associated with the given factory.
    *
    * @return a singleton output module
    */
  @throws[IOException]
  def create(): CpgOutputModule

  /**
    * A finalization method that potentially combines all CPGs added to any of the
    * output module created through this factory.
    */
  @throws[IOException]
  def persist(): Unit
}
