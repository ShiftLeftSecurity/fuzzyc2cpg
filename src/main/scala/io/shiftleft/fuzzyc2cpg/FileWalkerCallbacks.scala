package io.shiftleft.fuzzyc2cpg

import java.nio.file.{Path, Paths}

import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule
import io.shiftleft.fuzzyc2cpg.parser.{ModuleParser => ParserModuleParser}
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.{Edge, Node}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}

class FileWalkerCallbacks(outputModule: OutputModule) extends SourceFileListener {
  private val structureCpg = CpgStruct.newBuilder()

  /**
    * Callback invoked for each file
    * */
  override def visitFile(pathToFile: Path): Unit = {
    val driver = new AntlrCModuleParserDriver()
    val parser = new ParserModuleParser(driver)

    val fileNode = createFileNode(pathToFile)
    val namespaceBlock = createNamespaceBlockNode(pathToFile)
    structureCpg.addNode(fileNode)
    structureCpg.addNode(namespaceBlock)
    structureCpg.addEdge(Utils.newEdge(EdgeType.AST, namespaceBlock, fileNode))

    val astVisitor = new AstVisitor(outputModule, structureCpg, namespaceBlock)
    parser.addObserver(astVisitor)

    parser.parseFile(pathToFile.toString)
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

  private def createFileNode(pathToFile: Path): Node = {
    val nodeBuilder = Node.newBuilder()
      .setKey(IdPool.getNextId)
    val nameValue = PropertyValue.newBuilder()
      .setStringValue(pathToFile.toString)
    val nameProperty = Property.newBuilder()
      .setName(NodePropertyName.NAME)
      .setValue(nameValue)
    nodeBuilder.setType(NodeType.FILE)
      .addProperty(nameProperty)
    nodeBuilder.build()
  }

  private def createNamespaceBlockNode(pathToFile: Path): Node = {
    val nameProperty = Property.newBuilder()
      .setName(NodePropertyName.NAME)
      .setValue(PropertyValue.newBuilder().setStringValue("<global>").build());

    val fullNameProperty = Utils.newStringProperty(
      NodePropertyName.FULL_NAME,
        s"${pathToFile.toString}:<global>")

    Node.newBuilder()
      .setKey(IdPool.getNextId)
      .setType(NodeType.NAMESPACE_BLOCK)
      .addProperty(nameProperty)
      .addProperty(fullNameProperty)
      .build()
  }


  override def shutdown(): Unit = {
    outputStructuralCpg()
  }

  private def outputStructuralCpg(): Unit = {
    val outputFilename = Paths
      .get(Config.outputDirectory, "structural-cpg.proto")
      .toString();
    outputModule.output(structureCpg, outputFilename);
  }

  override def initialize(): Unit = {

  }
}

