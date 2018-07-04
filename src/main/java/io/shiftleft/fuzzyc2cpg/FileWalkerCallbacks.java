package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.walking.AstWalker;
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener;
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

class FileWalkerCallbacks extends SourceFileListener {

  AntlrCModuleParserDriver driver = new AntlrCModuleParserDriver();
  ModuleParser parser = new ModuleParser(driver);
  CpgStruct.Builder structureCpg = CpgStruct.newBuilder();

  protected AstWalker astWalker;
  protected String outputDir;

  /**
   * Callback invoked for each file
   * */

  @Override
  public void visitFile(Path pathToFile) {
    addFileNode(pathToFile);
    parser.parseFile(pathToFile.toString());
  }

  @Override
  public void initialize() {
    initializeDirectoryImporter();
    initializeWalker();
    initializeDatabase();
    parser.addObserver(astWalker);
  }

  public void setOutputDir(String anOutputDir) {
    outputDir = anOutputDir;
  }

  @Override
  public void shutdown() {
    shutdownDatabase();
  }

  protected void initializeWalker() {
    astWalker = new AstWalker();
  }


  protected void initializeDatabase() { }

  protected void initializeDirectoryImporter() { }


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

  protected void shutdownDatabase() {
    System.out.println(structureCpg);
  }

}

