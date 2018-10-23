package io.shiftleft.fuzzyc2cpg

import java.util

import io.shiftleft.fuzzyc2cpg.ast.{AstNode, AstNodeBuilder}
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule
import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriverObserver
import org.antlr.v4.runtime.ParserRuleContext

class AstVisitor(outputModule: OutputModule,
                 structureCpg: StructureCpg) extends ASTNodeVisitor with AntlrParserDriverObserver {

  /**
    * Callback triggered for each function definition
    * */
  override def visit(ast: FunctionDefBase): Unit =  {
    new FunctionDefHandler(structureCpg, outputModule).handle(ast)
  }

  /**
    * Callback triggered for every class/struct
    * */
  override def visit(ast: ClassDefStatement): Unit = {
    new ClassDefHandler(structureCpg).handle(ast)
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

  }

  override def endOfUnit(ctx: ParserRuleContext, filename: String): Unit = {

  }

  override def processItem(node: AstNode , nodeStack: util.Stack[AstNodeBuilder]): Unit = {
    node.accept(this)
  }
}

