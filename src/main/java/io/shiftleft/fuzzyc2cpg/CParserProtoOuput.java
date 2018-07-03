package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.outputmodules.ParserProtoOutput;
import io.shiftleft.fuzzyc2cpg.parser.ModuleParser;
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver;
import java.nio.file.Path;

class CParserProtoOuput extends ParserProtoOutput {

  AntlrCModuleParserDriver driver = new AntlrCModuleParserDriver();
  ModuleParser parser = new ModuleParser(driver);

  @Override
  public void visitFile(Path pathToFile) {
    parser.parseFile(pathToFile.toString());
  }

  @Override
  public void preVisitDirectory(Path dir) {

  }

  @Override
  public void postVisitDirectory(Path dir) {

  }

  @Override
  public void initialize() {
    super.initialize();
    parser.addObserver(astWalker);
  }

}