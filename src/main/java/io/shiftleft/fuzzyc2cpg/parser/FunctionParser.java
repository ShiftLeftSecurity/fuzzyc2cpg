package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;


public class FunctionParser {

  AntlrParserDriver driver;

  public FunctionParser(AntlrParserDriver aDriver) {
    driver = aDriver;
  }

  public void parseAndWalkString(String input) {
    driver.parseAndWalkString(input);
  }

  public ParseTree parseString(String input) throws ParserException {
    return driver.parseString(input);
  }

  public void parseAndWalkTokenStream(TokenSubStream tokens)
      throws ParserException {
    driver.parseAndWalkTokenStream(tokens);
  }

  public Parser getAntlrParser() {
    return driver.getAntlrParser();
  }

  public AntlrParserDriver getParser() {
    return driver;
  }

  public CompoundStatement getResult() {
    // The result is what's left on the stack in the end,
    // an AST rooted at a CompoundStatement node
    return driver.getResult();
  }

}
