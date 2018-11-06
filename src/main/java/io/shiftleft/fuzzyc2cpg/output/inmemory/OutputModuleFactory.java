package io.shiftleft.fuzzyc2cpg.output.inmemory;

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;
import io.shiftleft.queryprimitives.steps.starters.Cpg;

public class OutputModuleFactory implements CpgOutputModuleFactory<Cpg> {

  private OutputModule outputModule;

  @Override
  public CpgOutputModule create() {
    synchronized (this) {
      if (outputModule == null) {
        outputModule = new OutputModule();
      }
    }
    return outputModule;
  }

  @Override
  public Cpg getInternalGraph() {
    return outputModule.getInternalGraph();
  }

  @Override
  public void persist() {
    if (outputModule != null) {
      outputModule.persist();
    }
  }
}

