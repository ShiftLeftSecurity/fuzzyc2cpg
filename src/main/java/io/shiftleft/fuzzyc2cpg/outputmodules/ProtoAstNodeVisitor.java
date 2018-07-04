package io.shiftleft.fuzzyc2cpg.outputmodules;

// Stays alive during the lifetime of the program

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class ProtoAstNodeVisitor extends ASTNodeVisitor {

  /**
   * Callback triggered for each function definition
   * */

  public void visit(FunctionDefBase node) {

  }

  public void visit(ClassDefStatement node) {

  }

  public void visit(IdentifierDeclStatement node) {

  }

}
