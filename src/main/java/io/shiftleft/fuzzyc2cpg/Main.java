package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.fileWalker.OrderedWalker;
import io.shiftleft.fuzzyc2cpg.fileWalker.SourceFileWalker;
import java.io.IOException;

public class Main {

  private static CParserProtoOuput parser;
  private static SourceFileWalker sourceFileWalker = new OrderedWalker();

  public static void main(String [] args) {
    setupIndexer();

    String[] fileAndDirNames = {"input"};
    walkCodebase(fileAndDirNames);
  }

  private static void setupIndexer()
  {
    parser = new CParserProtoOuput();
    String outputDir = "out";
    parser.setOutputDir(outputDir);
    parser.initialize();
    sourceFileWalker.addListener(parser);
  }

  private static void walkCodebase(String[] fileAndDirNames)
  {
    try
    {
      sourceFileWalker.walk(fileAndDirNames);
    } catch (IOException err)
    {
      System.err.println("Error walking source files: " + err.getMessage());
    } finally
    {
      parser.shutdown();
    }
  }

}
