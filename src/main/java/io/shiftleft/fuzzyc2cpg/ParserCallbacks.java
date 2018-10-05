package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule;

public class ParserCallbacks extends ASTNodeVisitor {

  private final OutputModule outputModule;

  public ParserCallbacks(OutputModule outputModule) {
    this.outputModule = outputModule;
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

}
