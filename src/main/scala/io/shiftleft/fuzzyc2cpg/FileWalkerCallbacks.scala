package io.shiftleft.fuzzyc2cpg

import java.nio.file.{Path, Paths}

import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.fuzzyc2cpg.Utils._
import io.shiftleft.fuzzyc2cpg.filewalker.SourceFileListener
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}

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
    val globalNamespaceBlockNotInFileNode = createNamespaceBlockNode(None)

    structureCpg.addNode(globalNamespaceBlockNotInFileNode)
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

