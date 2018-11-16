package io.shiftleft.fuzzyc2cpg

import java.util

import io.shiftleft.fuzzyc2cpg.ast.{AstNode, AstNodeBuilder}
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.fuzzyc2cpg.astnew.{AstToCpgConverter, ProtoCpgAdapter}
import io.shiftleft.fuzzyc2cpg.cfg.{AstToCfgConverter, ProtoCfgAdapter}
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory
import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriverObserver
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import org.antlr.v4.runtime.ParserRuleContext

class AstVisitor(outputModuleFactory: CpgOutputModuleFactory[_],
                 structureCpg: CpgStruct.Builder,
                 astParentNode: Node)
  extends ASTNodeVisitor with AntlrParserDriverObserver {
  private var fileNameOption = Option.empty[String]

  /**
    * Callback triggered for each function definition
    * */
  override def visit(functionDef: FunctionDef): Unit =  {
    val outputModule = outputModuleFactory.create()
    outputModule.setClassAndMethodName(fileNameOption.get, functionDef.getName)

    val bodyCpg = CpgStruct.newBuilder()
    val cpgAdapter = new ProtoCpgAdapter(bodyCpg)
    val astToCpgConverter = new AstToCpgConverter(fileNameOption.get, astParentNode, cpgAdapter)
    astToCpgConverter.convert(functionDef)

    val graphAdapter = new ProtoCfgAdapter(bodyCpg, astToCpgConverter.getAstToProtoMapping)
    val astToCfgConverter = new AstToCfgConverter(
      astToCpgConverter.getMethodNode.get,
      astToCpgConverter.getMethodReturnNode.get,
      graphAdapter)
    astToCfgConverter.convert(functionDef)

    outputModule.persistCpg(bodyCpg)
  }

  /**
    * Callback triggered for every class/struct
    * */
  override def visit(classDefStatement: ClassDefStatement): Unit = {
    val cpgAdapter = new ProtoCpgAdapter(structureCpg)
    val astToCpgConverter = new AstToCpgConverter(fileNameOption.get, astParentNode, cpgAdapter)
    astToCpgConverter.convert(classDefStatement)
  }

  /**
    * Callback triggered for every global identifier declaration
    * */
  override def visit(node: IdentifierDeclStatement): Unit = {

  }

  override def begin(): Unit = {

  }

  override def end(): Unit = {

  }

  override def startOfUnit(ctx: ParserRuleContext, filename: String): Unit = {
    fileNameOption = Some(filename)
  }

  override def endOfUnit(ctx: ParserRuleContext, filename: String): Unit = {

  }

  override def processItem(node: AstNode , builderStack: util.Stack[AstNodeBuilder]): Unit = {
    node.accept(this)
  }
}

