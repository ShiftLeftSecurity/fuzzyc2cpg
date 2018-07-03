package io.shiftleft.fuzzyc2cpg.outputmodules.parser;

import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener;

public abstract class Parser extends SourceFileListener {

  protected ParserAstWalker astWalker;

  protected String outputDir;

  protected abstract void initializeDirectoryImporter();

  protected abstract void initializeWalker();

  protected abstract void initializeDatabase();

  protected abstract void shutdownDatabase();

  public void setOutputDir(String anOutputDir) {
    outputDir = anOutputDir;
  }

  @Override
  public void initialize() {
    initializeDirectoryImporter();
    initializeWalker();
    initializeDatabase();
  }

  @Override
  public void shutdown() {
    shutdownDatabase();
  }


}
