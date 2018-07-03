package io.shiftleft.fuzzyc2cpg.ast.functionDef;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;

public class ReturnType extends AstNode {

  String completeType;
  String baseType;

  public void setCompleteType(String aCompleteType) {
    completeType = aCompleteType;
  }

  public void setBaseType(String aBaseType) {
    baseType = aBaseType;
  }

}
