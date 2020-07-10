package io.shiftleft.fuzzyc2cpg

import java.nio.file.Path

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.passes.KeyPool
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName}
import io.shiftleft.codepropertygraph.generated.Languages
import io.shiftleft.fuzzyc2cpg.Utils.{getGlobalNamespaceBlockFullName, newEdge, newNode, _}
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.slf4j.LoggerFactory

import scala.collection.parallel.CollectionConverters._
import scala.collection.mutable

case class Global(usedTypes: mutable.Set[String] = new mutable.HashSet[String])

class CpgCreator(outputModuleFactory: CpgOutputModuleFactory) {

  private val logger = LoggerFactory.getLogger(getClass)
  private val cache = new DeclarationCache

  def runAndOutput(sourcePaths: Set[String], sourceFileExtensions: Set[String]): Unit = {
    val sourceFileNames = SourceFiles.determine(sourcePaths, sourceFileExtensions)
    val keyPools = KeyPools.obtain(sourceFileNames.size.toLong + 1)
    val globalKeyPool = keyPools.head
    val compilationUnitKeyPools = keyPools.slice(1, keyPools.size)
    val global = Global()

    addMetaDataAndPlaceholders(globalKeyPool)
    addCompilationUnits(sourceFileNames, compilationUnitKeyPools, global)
    addFunctionDeclarations(cache)
    addTypeNodes(global.usedTypes, globalKeyPool)
    outputModuleFactory.persist()
  }

  private def addMetaDataAndPlaceholders(keyPool: KeyPool): Unit = {

    def addMetaDataNode(cpg: CpgStruct.Builder): Unit = {
      val metaNode = newNode(NodeType.META_DATA)
        .setKey(keyPool.next)
        .addStringProperty(NodePropertyName.LANGUAGE, Languages.C)
        .build
      cpg.addNode(metaNode)
    }

    def addAnyTypeAndNamespaceBlock(cpg: CpgStruct.Builder): Unit = {
      val globalNamespaceBlockNotInFileNode = createNamespaceBlockNode(None, keyPool)
      cpg.addNode(globalNamespaceBlockNotInFileNode)
    }

    val cpg = CpgStruct.newBuilder()
    addMetaDataNode(cpg)
    addAnyTypeAndNamespaceBlock(cpg)
    outputModuleFactory.create().persistCpg(cpg)
  }

  // TODO improve fuzzyc2cpg namespace support. Currently, everything
  // is in the same global namespace so the code below is correct.
  private def addCompilationUnits(sourceFileNames: List[String], keyPools: List[KeyPool], global: Global): Unit = {
    sourceFileNames.zipWithIndex
      .map { case (filename, i) => (filename, keyPools(i)) }
      .par
      .foreach { case (filename, keyPool) => createCpgForCompilationUnit(filename, keyPool, global) }
  }

  private def createCpgForCompilationUnit(filename: String, keyPool: KeyPool, global: Global): Unit = {
    val (fileNode, namespaceBlock) = fileAndNamespaceGraph(filename, keyPool)

    // We call the module parser here and register the `astVisitor` to
    // receive callbacks as we walk the tree. The method body parser
    // will the invoked by `astVisitor` as we walk the tree

    val driver = new AntlrCModuleParserDriver()
    val astVisitor =
      new AstVisitor(outputModuleFactory, namespaceBlock, keyPool, cache, global)
    driver.addObserver(astVisitor)
    driver.setKeyPool(keyPool)
    driver.setOutputModuleFactory(outputModuleFactory)
    driver.setFileNode(fileNode)

    try {
      driver.parseAndWalkFile(filename)
    } catch {
      case ex: RuntimeException => {
        logger.warn("Cannot parse module: " + filename + ", skipping")
        logger.warn("Complete exception: ", ex)
      }
      case _: StackOverflowError => {
        logger.warn("Cannot parse module: " + filename + ", skipping, StackOverflow")
      }
    }
  }

  private def addFunctionDeclarations(cache: DeclarationCache): Unit = {
    cache.sortedSignatures.par.foreach { signature =>
      cache.getDeclarations(signature).foreach {
        case (_, bodyCpg) =>
          outputModuleFactory.create().persistCpg(bodyCpg)
      }
    }
  }

  private def addTypeNodes(usedTypes: mutable.Set[String], keyPool: KeyPool): Unit = {
    val cpg = CpgStruct.newBuilder()
    createTypeNodes(usedTypes, keyPool, cpg)
    outputModuleFactory.create().persistCpg(cpg)
  }

  private def fileAndNamespaceGraph(filename: String, keyPool: KeyPool): (Node, Node) = {

    def createFileNode(pathToFile: Path, keyPool: KeyPool): Node = {
      newNode(NodeType.FILE)
        .setKey(keyPool.next)
        .addStringProperty(NodePropertyName.NAME, pathToFile.toAbsolutePath.normalize.toString)
        .build()
    }

    val cpg = CpgStruct.newBuilder()

    val pathToFile = new java.io.File(filename).toPath
    val fileNode = createFileNode(pathToFile, keyPool)
    val namespaceBlock = createNamespaceBlockNode(Some(pathToFile), keyPool)
    cpg.addNode(fileNode)
    cpg.addNode(namespaceBlock)
    cpg.addEdge(newEdge(EdgeType.AST, namespaceBlock, fileNode))
    outputModuleFactory.create().persistCpg(cpg)
    (fileNode, namespaceBlock)
  }

  private def createNamespaceBlockNode(filePath: Option[Path], keyPool: KeyPool): Node = {
    newNode(NodeType.NAMESPACE_BLOCK)
      .setKey(keyPool.next)
      .addStringProperty(NodePropertyName.NAME, Defines.globalNamespaceName)
      .addStringProperty(NodePropertyName.FULL_NAME, getGlobalNamespaceBlockFullName(filePath.map(_.toString)))
      .build
  }

  private def createTypeNodes(usedTypes: mutable.Set[String], keyPool: KeyPool, cpg: CpgStruct.Builder): Unit = {
    usedTypes.toList.sorted
      .foreach { typeName =>
        val node = newNode(NodeType.TYPE)
          .setKey(keyPool.next)
          .addStringProperty(NodePropertyName.NAME, typeName)
          .addStringProperty(NodePropertyName.FULL_NAME, typeName)
          .addStringProperty(NodePropertyName.TYPE_DECL_FULL_NAME, typeName)
          .build
        cpg.addNode(node)
      }
  }

}
