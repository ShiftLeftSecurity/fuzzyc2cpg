package io.shiftleft.fuzzyc2cpg.antlrparsers.functionparser;

import static org.junit.Assert.assertEquals;

import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

import io.shiftleft.fuzzyc2cpg.FunctionParser;
import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriver;

public class FunctionCommentTests extends FunctionParserTestBase {

  @Test
  public void testLineComment() {
    String input = "// This is a comment!";
    FunctionParser parser = createHiddenParser(input);
    assertEquals(parser.getCurrentToken().getText(), "// This is a comment!");
  }

  @Test
  public void testLineCommentAfterStatement() {
    String input = "int x = 5; // This is a comment!";
    FunctionParser parser = createHiddenParser(input);
    assertEquals(parser.getCurrentToken().getText(), "// This is a comment!");
  }

  @Test
  public void testBlockComment() {
    String input = "/* This is a block comment! */";
    FunctionParser parser = createHiddenParser(input);
    assertEquals(parser.getCurrentToken().getText(), "/* This is a block comment! */");
  }

  @Test
  public void testBlockCommentWithinStatement() {
    String input = "int /* This is a block comment! */ x = 5;";
    FunctionParser parser = createHiddenParser(input);
    assertEquals(parser.getCurrentToken().getText(), "/* This is a block comment! */");
  }

  private void compareParses(String actual, String expected) {
    AntlrParserDriver functionParser = createFunctionDriver();
    ParseTree actualTree = functionParser.parseString(actual);
    ParseTree expectedTree = functionParser.parseString(expected);
    String actualOutput = actualTree.toStringTree(functionParser.getAntlrParser());
    String expectedOutput = expectedTree.toStringTree(functionParser.getAntlrParser());
    assertEquals(actualOutput, expectedOutput);
  }

  @Test
  public void testLineCommentDriver() {
    String inputWithComment = "// This is a comment!";
    String inputWithoutComment = "";
    compareParses(inputWithComment, inputWithoutComment);
  }

  @Test
  public void testLineCommentAfterStatementDriver() {
    String inputWithComment = "int x = 5; // This is a comment!";
    String inputWithoutComment = "int x = 5;";
    compareParses(inputWithComment, inputWithoutComment);
  }

  @Test
  public void testBlockCommentDriver() {
    String inputWithComment = "/* This is a block comment! */";
    String inputWithoutComment = "";
    compareParses(inputWithComment, inputWithoutComment);
  }

  @Test
  public void testBlockCommentWithinStatementDriver() {
    String inputWithComment = "int /* This is a block comment! */ x = 5;";
    String inputWithoutComment = "int x = 5;";
    compareParses(inputWithComment, inputWithoutComment);
  }
}
