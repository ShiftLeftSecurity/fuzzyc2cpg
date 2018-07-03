package io.shiftleft.fuzzyc2cpg.ast.declarations;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;

public class IdentifierDeclType extends AstNode {

  public String baseType;
  public String completeType;

  public String getEscapedCodeStr() {
    return completeType;
  }

}
