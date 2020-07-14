package io.shiftleft.fuzzyc2cpg.passes.compilationunit

import io.shiftleft.codepropertygraph.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.{File, NamespaceBlock}
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.fuzzyc2cpg.parser.{AntlrParserDriverObserver, TokenSubStream}
import io.shiftleft.fuzzyc2cpg.{DeclarationCache, Global, ModuleLexer, ModuleParser}
import io.shiftleft.passes.{DiffGraph, KeyPool, ParallelCpgPass}
import io.shiftleft.semanticcpg.language._
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.{CharStream, ParserRuleContext}
import org.slf4j.LoggerFactory

class CompilationUnitPass(filenames: List[String], cpg: Cpg, keyPools: Option[Iterator[KeyPool]])
    extends ParallelCpgPass[String](cpg, keyPools = keyPools) {

  private val logger = LoggerFactory.getLogger(getClass)
  val global: Global = Global()
  val cache = new DeclarationCache()

  override def partIterator: Iterator[String] = filenames.iterator

  override def runOnPart(filename: String): Iterator[DiffGraph] = {

    fileAndNamespaceBlock(filename) match {
      case Some((fileNode, namespaceBlock)) =>
        val driver = new AntlrCModuleParserDriver()
        val astVisitor =
          new AstVisitor(namespaceBlock, cache, global)
        driver.addObserver(astVisitor)
        driver.setFileNode(fileNode)
        try {
          val diffGraph = driver.parseAndWalkFile(filename)
          Iterator(diffGraph.build)
        } catch {
          case ex: RuntimeException => {
            logger.warn("Cannot parse module: " + filename + ", skipping")
            logger.warn("Complete exception: ", ex)
            Iterator()
          }
          case _: StackOverflowError => {
            logger.warn("Cannot parse module: " + filename + ", skipping, StackOverflow")
            Iterator()
          }
        }
      case None =>
        logger.warn("Invalid File/Namspace Graph")
        Iterator()
    }
  }

  private def fileAndNamespaceBlock(filename: String): Option[(File, NamespaceBlock)] = {
    cpg.file
      .name(new java.io.File(filename).getAbsolutePath)
      .l
      .flatMap { f =>
        f.start.namespaceBlock.l.map(n => (f, n))
      }
      .headOption
  }

  class AstVisitor(astParentNode: NamespaceBlock, cache: DeclarationCache, global: Global)
      extends ASTNodeVisitor
      with AntlrParserDriverObserver {

    var filenameOption: Option[String] = _

    override def begin(): Unit = {}

    override def end(): Unit = {}

    override def startOfUnit(ctx: ParserRuleContext, filename: String): Unit = {
      filenameOption = Some(filename)
    }

    override def endOfUnit(ctx: ParserRuleContext, filename: String): Unit = {}

    def processItem[T <: io.shiftleft.fuzzyc2cpg.ast.AstNode](
        node: T,
        builderStack: java.util.Stack[
          io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder[_ <: io.shiftleft.fuzzyc2cpg.ast.AstNode]]): Unit = {
      ???
    }

  }

  class AntlrCModuleParserDriver() extends AntlrParserDriver {
    setListener(new CModuleParserTreeListener(this))

    override def parseTokenStreamImpl(tokens: TokenSubStream): ParseTree = {
      val parser = new ModuleParser(tokens)
      setAntlrParser(parser)
      var tree: ModuleParser.CodeContext = null
      try {
        setSLLMode(parser)
        tree = parser.code
      } catch {
        case ex: RuntimeException =>
          if (isRecognitionException(ex)) {
            tokens.reset()
            setLLStarMode(parser)
            tree = parser.code
          }
      }
      tree
    }

    override def createLexer(input: CharStream) = new ModuleLexer(input)
  }

}
