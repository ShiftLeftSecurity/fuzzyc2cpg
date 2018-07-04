package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.outputmodules.ProtoAstWalker;
import io.shiftleft.fuzzyc2cpg.outputmodules.Parser;
import io.shiftleft.fuzzyc2cpg.parser.ModuleParser;
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver;
import java.nio.file.Path;

class CParserProtoOutput extends Parser {

  AntlrCModuleParserDriver driver = new AntlrCModuleParserDriver();
  ModuleParser parser = new ModuleParser(driver);

  @Override
  protected void initializeWalker() {
    astWalker = new ProtoAstWalker();
  }

  @Override
  public void initialize() {
    super.initialize();
    parser.addObserver(astWalker);
  }

  @Override protected void initializeDatabase() { }

  @Override protected void initializeDirectoryImporter() { }

  /**
   * Callback invoked for each file
   * */

  @Override
  public void visitFile(Path pathToFile) {
    parser.parseFile(pathToFile.toString());
  }

  /**
   * Callback invoked upon entering a directory
   * */

  @Override
  public void preVisitDirectory(Path dir) {

  }

  /**
   * Callback invoked upon leaving a directory
   * */

  @Override
  public void postVisitDirectory(Path dir) {

  }

  @Override
  protected void shutdownDatabase() {

  }

}