package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule;
import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriverObserver;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Stack;

public class AstVisitor extends ASTNodeVisitor implements AntlrParserDriverObserver {

  private final OutputModule outputModule;
  private final StructureCpg structureCpg;

  public AstVisitor(OutputModule outputModule, StructureCpg structureCpg) {
    this.outputModule = outputModule;
    this.structureCpg = structureCpg;
  }

  /**
   * Callback triggered for each function definition
   * */

  public void visit(FunctionDefBase ast) {
    (new FunctionDefHandler(structureCpg, outputModule)).handle(ast);
  }

  /**
   * Callback triggered for every class/struct
   * */

  public void visit(ClassDefStatement ast) {
    (new ClassDefHandler(structureCpg)).handle(ast);
  }

  /**
   * Callback triggered for every global identifier declaration
   * */
  public void visit(IdentifierDeclStatement node) {

  }

  @Override
  public void begin() {

  }

  @Override
  public void end() {

  }

  @Override
  public void startOfUnit(ParserRuleContext ctx, String filename) {

  }

  @Override
  public void endOfUnit(ParserRuleContext ctx, String filename) {

  }

  @Override
  public void processItem(AstNode node, Stack<AstNodeBuilder> nodeStack) {
    node.accept(this);
  }
}
