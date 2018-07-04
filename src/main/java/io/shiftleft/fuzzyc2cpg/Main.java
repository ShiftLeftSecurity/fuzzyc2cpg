package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.filewalker.OrderedWalker;
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileWalker;
import java.io.IOException;

public class Main {

  private static CParserProtoOutput parser;
  private static SourceFileWalker sourceFileWalker = new OrderedWalker();

  public static void main(String[] args) {
    setupParser();

    String[] fileAndDirNames = {"input"};
    walkCodebase(fileAndDirNames);
  }

  /**
   * Create a new parser and register it as a listener
   * for the source file walker, such that it can be
   * called for each source file.
   * */
  private static void setupParser() {
    parser = new CParserProtoOutput();
    String outputDir = "out";
    parser.setOutputDir(outputDir);
    parser.initialize();
    sourceFileWalker.addListener(parser);
  }

  private static void walkCodebase(String[] fileAndDirNames) {
    try {
      sourceFileWalker.walk(fileAndDirNames);
    } catch (IOException err) {
      System.err.println("Error walking source files: " + err.getMessage());
    } finally {
      parser.shutdown();
    }
  }

}
