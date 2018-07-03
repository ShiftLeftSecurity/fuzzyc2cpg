package io.shiftleft.fuzzyc2cpg.ast.langc.functiondef;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;

public class ParameterType extends AstNode {

  String completeType = "";
  String baseType = "";

  @Override
  public String getEscapedCodeStr() {
    setCodeStr(completeType);
    return getCodeStr();
  }

  public void setCompleteType(String completeType) {
    this.completeType = completeType;
  }

  public void setBaseType(String baseType) {
    this.baseType = baseType;
  }

}
