package io.shiftleft.fuzzyc2cpg.output.protobuf;

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class OutputModuleFactory implements CpgOutputModuleFactory {

  private final List<OutputModule> outputModules = new ArrayList<>();
  private final boolean writeToDisk;
  private final Path protoTempDir;
  private final String outputFilename;

  public OutputModuleFactory(String outputFilename,
                             boolean writeToDisk) throws IOException {
    this.writeToDisk = writeToDisk;
    this.protoTempDir = Files.createTempDirectory("proto");
    this.outputFilename = outputFilename;
  }

  @Override
  public CpgOutputModule create() {
    OutputModule outputModule = new OutputModule(writeToDisk, protoTempDir);
    synchronized (this) {
      outputModules.add(outputModule);
    }
    return outputModule;
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
