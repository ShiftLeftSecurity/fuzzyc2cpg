package io.shiftleft.fuzzyc2cpg.parser;

public class ModuleParser {

  AntlrParserDriver parserDriver;

  public ModuleParser(AntlrParserDriver driver) {
    parserDriver = driver;
  }

  public void parseFile(String filename) {

    try {
      parserDriver.parseAndWalkFile(filename);
    } catch (ParserException ex) {
      System.err.println("Error parsing file: " + filename);
    }
  }

  public void addObserver(AntlrParserDriverObserver anObserver) {
    parserDriver.addObserver(anObserver);
  }

  /*
   * Testing
   **/

  public void parseString(String code) {
    try {
      parserDriver.parseAndWalkString(code);
    } catch (ParserException ex) {
      System.err.println("Error parsing string.");
    }
  }

}
