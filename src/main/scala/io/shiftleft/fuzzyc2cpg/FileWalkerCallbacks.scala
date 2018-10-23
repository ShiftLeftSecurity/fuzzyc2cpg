package io.shiftleft.fuzzyc2cpg

import java.nio.file.{Path, Paths}

import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule
import io.shiftleft.fuzzyc2cpg.parser.{ModuleParser => ParserModuleParser}
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.{Edge, Node}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{NodePropertyName, PropertyValue}

class FileWalkerCallbacks(outputModule: OutputModule) extends SourceFileListener {
  private final val driver = new AntlrCModuleParserDriver()
  private final val parser = new ParserModuleParser(driver)
  private final val structureCpg = new StructureCpg()
  private final val astVisitor = new AstVisitor(outputModule, structureCpg)

  initializeGlobalNamespaceBlock()
  parser.addObserver(astVisitor)


  /**
    * Create a placeholder namespace node named '<global>' for all
    * types and methods not explicitly placed in a namespace.
    * */

  private def initializeGlobalNamespaceBlock(): Unit = {
    val nameProperty = Property.newBuilder()
      .setName(NodePropertyName.NAME)
      .setValue(PropertyValue.newBuilder().setStringValue("<global>").build());

    structureCpg.setNamespaceBlockNode(
      Node.newBuilder()
        .setKey(IdPool.getNextId())
        .setType(NodeType.NAMESPACE_BLOCK)
        .addProperty(nameProperty)
        .build()
    )
  }

  /**
    * Callback invoked for each file
    * */
  override def visitFile(pathToFile: Path): Unit = {
    val fileNode = addFileNode(pathToFile)
    connectFileNodeToNamespaceBlock(fileNode)
    parser.parseFile(pathToFile.toString())
  }

  private def connectFileNodeToNamespaceBlock(fileNode: Node): Unit = {
    val edgeBuilder = Edge.newBuilder()
      .setType(EdgeType.AST)
      .setSrc(fileNode.getKey())
      .setDst(structureCpg.getNamespaceBlockNode().getKey());
    structureCpg.addEdge(edgeBuilder.build());
  }

  /**
    * Callback invoked upon entering a directory
    * */
  override def preVisitDirectory(dir: Path): Unit = {

  }

  /**
    * Callback invoked upon leaving a directory
    * */
  override def postVisitDirectory(dir: Path): Unit = {

  }

  private def addFileNode(pathToFile: Path): Node = {
    val nodeBuilder = Node.newBuilder()
      .setKey(IdPool.getNextId())
    val nameValue = PropertyValue.newBuilder()
      .setStringValue(pathToFile.toString())
    val nameProperty = Property.newBuilder()
      .setName(NodePropertyName.NAME)
      .setValue(nameValue)
    nodeBuilder.setType(NodeType.FILE)
      .addProperty(nameProperty)
    val fileNode = nodeBuilder.build()
    structureCpg.addNode(fileNode)
    fileNode
  }

  override def shutdown(): Unit = {
    outputStructuralCpg()
  }

  private def outputStructuralCpg(): Unit = {
    val outputFilename = Paths
      .get(Config.outputDirectory, "structural-cpg.proto")
      .toString();
    outputModule.output(structureCpg.getCpg(), outputFilename);
  }

  override def initialize(): Unit = {

  }
}

