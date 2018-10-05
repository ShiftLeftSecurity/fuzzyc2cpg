package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.walking.AstWalker;
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener;
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule;
import io.shiftleft.fuzzyc2cpg.parser.ModuleParser;
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Builder;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Property;
import io.shiftleft.proto.cpg.Cpg.NodePropertyName;
import io.shiftleft.proto.cpg.Cpg.PropertyValue;
import java.nio.file.Path;
import java.nio.file.Paths;

class FileWalkerCallbacks extends SourceFileListener {

  AntlrCModuleParserDriver driver = new AntlrCModuleParserDriver();
  ModuleParser parser = new ModuleParser(driver);

  StructureCpg structureCpg;
  AstWalker astWalker;
  OutputModule outputModule;

  public FileWalkerCallbacks(OutputModule outputModule) {
    this.outputModule = outputModule;
  }

  @Override
  public void initialize() {
    initializeStructureCpg();
    initializeWalker();
    parser.addObserver(astWalker);
  }

  private void initializeStructureCpg() {
    structureCpg = new StructureCpg();
    initializeGlobalNamespaceBlock();
  }

  /**
   * Create a placeholder namespace node named '<global>' for all
   * types and methods not explicitly placed in a namespace.
   * */

  private void initializeGlobalNamespaceBlock() {
    Property.Builder nameProperty = Property.newBuilder()
        .setName(NodePropertyName.NAME)
        .setValue(PropertyValue.newBuilder().setStringValue("<global>").build());

    structureCpg.setNamespaceBlockNode(
        Node.newBuilder()
            .setKey(IdPool.getNextId())
            .setType(NodeType.NAMESPACE_BLOCK)
            .addProperty(nameProperty)
            .build()
    );
  }

  private void initializeWalker() {
    astWalker = new AstWalker();
    astWalker.setOutputModule(outputModule);
    astWalker.setStructureCpg(structureCpg);
  }

  /**
   * Callback invoked for each file
   * */

  @Override
  public void visitFile(Path pathToFile) {
    Node fileNode = addFileNode(pathToFile);
    connectFileNodeToNamespaceBlock(fileNode);
    parser.parseFile(pathToFile.toString());
  }

  private void connectFileNodeToNamespaceBlock(Node fileNode) {
    Edge.Builder edgeBuilder = Edge.newBuilder()
        .setType(EdgeType.AST)
        .setSrc(fileNode.getKey())
        .setDst(structureCpg.getNamespaceBlockNode().getKey());
    structureCpg.addEdge(edgeBuilder.build());
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

  private Node addFileNode(Path pathToFile) {
    Builder nodeBuilder = Node.newBuilder()
        .setKey(IdPool.getNextId());
    PropertyValue.Builder nameValue = PropertyValue.newBuilder()
        .setStringValue(pathToFile.toString());
    Property.Builder nameProperty = Property.newBuilder()
        .setName(NodePropertyName.NAME)
        .setValue(nameValue);
    nodeBuilder.setType(NodeType.FILE)
        .addProperty(nameProperty);
    Node fileNode = nodeBuilder.build();
    structureCpg.addNode(fileNode);
    return fileNode;
  }


  @Override
  public void shutdown() {
    outputStructuralCpg();
  }

  private void outputStructuralCpg() {
    String outputFilename = Paths
        .get(Config.outputDirectory, "structural-cpg.proto")
        .toString();
    outputModule.output(structureCpg.getCpg(), outputFilename);
  }

}

