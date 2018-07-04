package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.outputmodules.ProtoAstWalker;
import io.shiftleft.fuzzyc2cpg.parser.Parser;
import io.shiftleft.fuzzyc2cpg.parser.ModuleParser;
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Builder;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Property;
import io.shiftleft.proto.cpg.Cpg.NodePropertyName;
import io.shiftleft.proto.cpg.Cpg.PropertyValue;
import java.nio.file.Path;

class CParserProtoOutput extends Parser {

  AntlrCModuleParserDriver driver = new AntlrCModuleParserDriver();
  ModuleParser parser = new ModuleParser(driver);
  CpgStruct.Builder structureCpg = CpgStruct.newBuilder();

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
    addFileNode(pathToFile);
    parser.parseFile(pathToFile.toString());
  }

  private void addFileNode(Path pathToFile) {
    Builder nodeBuilder = Node.newBuilder();
    PropertyValue.Builder nameValue = PropertyValue.newBuilder()
        .setStringValue(pathToFile.toString());
    Property.Builder nameProperty = Property.newBuilder()
        .setName(NodePropertyName.NAME)
        .setValue(nameValue);
    nodeBuilder.setType(NodeType.FILE)
        .addProperty(nameProperty);
    structureCpg.addNode(nodeBuilder);
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
    System.out.println(structureCpg);
  }

}