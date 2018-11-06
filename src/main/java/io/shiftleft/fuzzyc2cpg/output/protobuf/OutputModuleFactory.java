package io.shiftleft.fuzzyc2cpg.output.protobuf;

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

public class OutputModuleFactory implements CpgOutputModuleFactory<List<CpgStruct>> {

  private final List<OutputModule> outputModules = new ArrayList<>();
  private final boolean writeToDisk;
  private final boolean keepInternalGraph;
  private final Path protoTempDir;
  private final String outputFilename;

  public OutputModuleFactory(String outputFilename,
                             boolean writeToDisk,
                             boolean keepInternalGraph) throws IOException {
    this.writeToDisk = writeToDisk;
    this.keepInternalGraph = keepInternalGraph;
    this.protoTempDir = Files.createTempDirectory("proto");
    this.outputFilename = outputFilename;
  }

  @Override
  public CpgOutputModule create() {
    OutputModule outputModule = new OutputModule(keepInternalGraph, writeToDisk, protoTempDir);
    synchronized (this) {
      outputModules.add(outputModule);
    }
    return outputModule;
  }

  @Override
  public List<CpgStruct> getInternalGraph() {
    if (!keepInternalGraph) {
      throw new RuntimeException("Requested internal graph but `keepInternalGraph` is false");
    }
    List<CpgStruct> result;

    synchronized (this) {
      result = outputModules.stream()
          .map(OutputModule::getProtoCpg)
          .collect(Collectors.toList());
    }
    return result;
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
  public void persist() throws IOException {
    if (writeToDisk) {
      try {
        ThreadedZipper threadedZipper = new ThreadedZipper(protoTempDir, outputFilename);
        threadedZipper.start();
        // wait until the thread is finished
        // if we don't wait, the output folder
        // may be deleted and and we get null pointer
        threadedZipper.join();
      } catch (InterruptedException interruptedException) {
        throw new IOException(interruptedException);
      }
    }
    if (this.protoTempDir != null && Files.exists(this.protoTempDir)) {
      FileUtils.deleteDirectory(this.protoTempDir.toFile());
    }
  }
}
