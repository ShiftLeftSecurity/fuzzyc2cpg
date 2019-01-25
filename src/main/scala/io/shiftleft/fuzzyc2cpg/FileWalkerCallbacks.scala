package io.shiftleft.fuzzyc2cpg

import java.nio.file.{Path, Paths}

import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.{Edge, Node}
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}

class FileWalkerCallbacks(outputModuleFactory: CpgOutputModuleFactory[_])
  extends SourceFileListener {
  private val structureCpg = CpgStruct.newBuilder()

  /**
    * Callback invoked for each file
    * */
  override def visitFile(pathToFile: Path): Unit = {
    val driver = new AntlrCModuleParserDriver()

    val fileNode = createFileNode(pathToFile)
    val namespaceBlock = createNamespaceBlockNode(Some(pathToFile))
    structureCpg.addNode(fileNode)
    structureCpg.addNode(namespaceBlock)
    structureCpg.addEdge(newEdge(EdgeType.AST, namespaceBlock, fileNode))

    val astVisitor = new AstVisitor(outputModuleFactory, structureCpg, namespaceBlock)
    driver.addObserver(astVisitor)

    driver.parseAndWalkFile(pathToFile.toString)
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

  private def addAnyTypeAndNamespacBlock(): Unit = {
    val anyTypeNode = createAnyTypeNode()
    val anyTypeDeclNode = createAnyTypeDeclNode()
    val globalNamespaceBlockNotInFileNode = createNamespaceBlockNode(None)

    structureCpg.addNode(anyTypeNode)
    structureCpg.addNode(anyTypeDeclNode)
    structureCpg.addNode(globalNamespaceBlockNotInFileNode)
  }

  private def createAnyTypeNode(): Node = {
    newNode(NodeType.TYPE)
      .addStringProperty(NodePropertyName.NAME, Defines.anyTypeName)
      .addStringProperty(NodePropertyName.FULL_NAME, Defines.anyTypeName)
      .addStringProperty(NodePropertyName.TYPE_DECL_FULL_NAME, Defines.anyTypeName)
      .build()
  }

  private def createAnyTypeDeclNode(): Node = {
    newNode(NodeType.TYPE_DECL)
      .addStringProperty(NodePropertyName.NAME, Defines.anyTypeName)
      .addStringProperty(NodePropertyName.FULL_NAME, Defines.anyTypeName)
      .addBooleanProperty(NodePropertyName.IS_EXTERNAL, false)
      .addStringProperty(NodePropertyName.AST_PARENT_TYPE, NodeType.NAMESPACE_BLOCK.toString)
      .addStringProperty(NodePropertyName.AST_PARENT_FULL_NAME, getGlobalNamespaceBlockFullName(None))
      .build
  }

  private def createFileNode(pathToFile: Path): Node = {
    newNode(NodeType.FILE)
      .addStringProperty(NodePropertyName.NAME, pathToFile.toString)
      .build()
  }

  private def createNamespaceBlockNode(filePath: Option[Path]): Node = {
      newNode(NodeType.NAMESPACE_BLOCK)
        .addStringProperty(NodePropertyName.NAME, Defines.globalNamespaceName)
        .addStringProperty(NodePropertyName.FULL_NAME, getGlobalNamespaceBlockFullName(filePath.map(_.toString)))
        .build
  }

  private def addMetaDataNode(): Unit = {
    // TODO replace FUZZYC through define from CPG.
    val metaNode = newNode(NodeType.META_DATA)
      .addStringProperty(NodePropertyName.LANGUAGE, Languages.C)
      .build

    structureCpg.addNode(metaNode)
  }

  override def shutdown(): Unit = {
    addMetaDataNode()
    addAnyTypeAndNamespacBlock()
    outputStructuralCpg()
    outputModuleFactory.persist()
  }

  private def outputStructuralCpg(): Unit = {
    val outputFilename = Paths
      .get(Config.outputDirectory, "structural-cpg.proto")
      .toString();
    val outputModule = outputModuleFactory.create()
    outputModule.setOutputIdentifier("__structural__")
    outputModule.persistCpg(structureCpg);
  }

  override def initialize(): Unit = {

  }
}

