package io.shiftleft.fuzzyc2cpg.output.protobuf;

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.shiftleft.proto.cpg.Cpg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputModuleFactory implements CpgOutputModuleFactory {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private final boolean writeToDisk;
  private final String outputFilename;
  private final BlockingQueue<Cpg.CpgStruct.Builder> queue;

  public OutputModuleFactory(String outputFilename,
                             boolean writeToDisk) {
    this.writeToDisk = writeToDisk;
    this.outputFilename = outputFilename;
    this.queue = new LinkedBlockingQueue<>();
  }

  public BlockingQueue<Cpg.CpgStruct.Builder> getQueue() {
    return queue;
  }

  public String getOutputFilename() {
    return outputFilename;
  }

  @Override
  public CpgOutputModule create() {
    return new OutputModule(queue);
  }

  /**
   * Store collected CPGs into the output directory specified
   * for this output module.
   * Note: This method should be called only once all intermediate CPGs
   * have been processed and collected.
   * If the output module was configured to combine intermediate CPGs into a single
   * one, we will combine individual proto files.
   * */
  @Override
  public void persist() {
    if (writeToDisk) {
      try {
        Cpg.CpgStruct.Builder endMarker =
                Cpg.CpgStruct.newBuilder()
                        .addNode(Cpg.CpgStruct.Node.newBuilder().setKey(-1));
        queue.put(endMarker);
      } catch (InterruptedException e) {
        logger.warn("Interrupted during persist operation");
      }
    }
  }
}
