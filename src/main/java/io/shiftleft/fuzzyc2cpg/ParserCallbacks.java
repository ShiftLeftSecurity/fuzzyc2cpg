package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class ParserCallbacks extends ASTNodeVisitor {

  /**
   * Callback triggered for each function definition
   * */

  public void visit(FunctionDefBase ast) {
    (new FunctionDefHandler(structureCpg)).handle(ast);
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
