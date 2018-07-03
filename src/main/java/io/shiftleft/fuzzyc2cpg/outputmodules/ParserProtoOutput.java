package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.fuzzyc2cpg.outputmodules.parser.Parser;

public abstract class ParserProtoOutput extends Parser {

  @Override
  protected void initializeWalker() {
    astWalker = new ProtoAstWalker();
  }

  @Override
  protected void initializeDirectoryImporter() {

  }

  @Override
  protected void initializeDatabase() {

  }

  @Override
  protected void shutdownDatabase() {

  }

}
