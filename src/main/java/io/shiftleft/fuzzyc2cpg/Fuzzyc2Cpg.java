package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.filewalker.OrderedWalker;
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileWalker;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;
import java.io.File;
import java.io.IOException;

public class Fuzzyc2Cpg {

  private FileWalkerCallbacks parser;
  private SourceFileWalker sourceFileWalker = new OrderedWalker();

  public Fuzzyc2Cpg(CpgOutputModuleFactory<?> outputModuleFactory) {
    setupParser(outputModuleFactory);
  }

  /**
   * Create a new parser and register it as a listener
   * for the source file walker, such that it can be
   * called for each source file.
   */
  private void setupParser(CpgOutputModuleFactory<?> outputModule) {
    parser = new FileWalkerCallbacks(outputModule);
    parser.initialize();
    sourceFileWalker.addListener(parser);
  }

  public void runAndOutput(String [] fileAndDirNames) {
    createOutputDirectory();
    walkCodebase(fileAndDirNames);
  }

  private void createOutputDirectory() {
    new File(
        Config.outputDirectory
    ).mkdirs();
  }

  private void walkCodebase(String[] fileAndDirNames) {
    try {
      sourceFileWalker.walk(fileAndDirNames);
    } catch (IOException err) {
      System.err.println("Error walking source files: " + err.getMessage());
    } finally {
      parser.shutdown();
    }
  }

}
