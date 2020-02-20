package io.shiftleft.fuzzyc2cpg.output.protobuf;

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;

import java.util.concurrent.BlockingQueue;

public class OutputModule implements CpgOutputModule {
  private final BlockingQueue<CpgStruct> queue;

  public OutputModule(BlockingQueue<CpgStruct> queue) {
    this.queue = queue;
  }

  @Override
  public void setOutputIdentifier(String identifier) {
  }

  /**
   * This is called for each code property graph. There is one
   * code property graph per method, and one graph for the overall
   * program structure.
   * */

  @Override
  public void persistCpg(CpgStruct.Builder cpg) {
    queue.add(cpg.build());
  }

}
