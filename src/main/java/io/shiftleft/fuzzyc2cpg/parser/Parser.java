package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.walking.AstWalker;
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener;;

public abstract class Parser extends SourceFileListener {

  protected AstWalker astWalker;

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
