package io.shiftleft.fuzzyc2cpg

import java.util

import io.shiftleft.fuzzyc2cpg.adapter.ProtoCpgAdapter
import io.shiftleft.fuzzyc2cpg.ast.{AstNode, AstNodeBuilder}
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.fuzzyc2cpg.astnew.AstToCpgConverter
import io.shiftleft.fuzzyc2cpg.cfg.AstToCfgConverter
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriverObserver
import io.shiftleft.passes.KeyPool
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import org.antlr.v4.runtime.ParserRuleContext

class AstVisitor(outputModuleFactory: CpgOutputModuleFactory,
                 structureCpg: CpgStruct.Builder,
                 astParentNode: Node,
                 keyPool: KeyPool,
                 cache: FuzzyC2CpgCache)
    extends ASTNodeVisitor
    with AntlrParserDriverObserver {
  private var fileNameOption = Option.empty[String]

  /**
    * Callback triggered for each function definition
    * */
  override def visit(functionDef: FunctionDef): Unit = {
    val outputModule = outputModuleFactory.create()
    val outputIdentifier = s"${fileNameOption.get}${functionDef.getName}" +
      s"${functionDef.getLocation.startLine}${functionDef.getLocation.endLine}"
    outputModule.setOutputIdentifier(outputIdentifier)

    val bodyCpg = CpgStruct.newBuilder()
    val cpgAdapter = new ProtoCpgAdapter(bodyCpg, keyPool)
    val astToCpgConverter =
      new AstToCpgConverter(astParentNode, cpgAdapter)
    astToCpgConverter.convert(functionDef)

    val astToCfgConverter =
      new AstToCfgConverter(astToCpgConverter.getMethodNode.get, astToCpgConverter.getMethodReturnNode.get, cpgAdapter)
    astToCfgConverter.convert(functionDef)

    if (functionDef.isOnlyDeclaration) {
      // Do not persist the declaration. It may be that we encounter a
      // corresponding definition, in which case the declaration will be
      // removed again and is never persisted. Persisting of declarations
      // happens after concurrent processing of compilation units.
      cache.add(functionDef.getFunctionSignature(false), outputIdentifier, bodyCpg)
    } else {
      cache.remove(functionDef.getFunctionSignature(false))
      outputModule.persistCpg(bodyCpg)
    }
  }

  /**
    * Callback triggered for every class/struct
    * */
  override def visit(classDefStatement: ClassDefStatement): Unit = {
    val cpgAdapter = new ProtoCpgAdapter(structureCpg, keyPool)
    val astToCpgConverter =
      new AstToCpgConverter(astParentNode, cpgAdapter)
    astToCpgConverter.convert(classDefStatement)
  }

  /**
    * Callback triggered for every global identifier declaration
    * */
  override def visit(identifierDeclStmt: IdentifierDeclStatement): Unit = {
    val cpgAdapter = new ProtoCpgAdapter(structureCpg, keyPool)
    val astToCpgConverter =
      new AstToCpgConverter(astParentNode, cpgAdapter)
    astToCpgConverter.convert(identifierDeclStmt)
  }

  override def begin(): Unit = {}

  override def end(): Unit = {}

  override def startOfUnit(ctx: ParserRuleContext, filename: String): Unit = {
    fileNameOption = Some(filename)
  }

  override def endOfUnit(ctx: ParserRuleContext, filename: String): Unit = {}

  override def processItem[T <: AstNode](node: T, builderStack: util.Stack[AstNodeBuilder[_ <: AstNode]]): Unit = {
    node.accept(this)
  }
}
