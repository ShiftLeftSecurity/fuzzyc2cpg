package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.filewalker.OrderedWalker;
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileWalker;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;
import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory;

import java.io.File;
import java.io.IOException;

public class Fuzzyc2Cpg {

  private FileWalkerCallbacks parser;
  private SourceFileWalker sourceFileWalker = new OrderedWalker();

  /**
   * Construct a FuzzyC2CPG instance that employs the default
   * protobuf output module.
   *
   * @param outputPath the filename for the output CPG
   * */
  public Fuzzyc2Cpg(String outputPath) throws IOException {
    this(new OutputModuleFactory(outputPath,
            true, false));
  }

  /**
   * Construct a FuzzyC2CPG instance that employs the
   * output module created by the given output module factory.
   *
   * @param outputModuleFactory the factory
   * */
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
    File outputDir = new File(Config.outputDirectory);
    outputDir.mkdir();
    outputDir.deleteOnExit();
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
