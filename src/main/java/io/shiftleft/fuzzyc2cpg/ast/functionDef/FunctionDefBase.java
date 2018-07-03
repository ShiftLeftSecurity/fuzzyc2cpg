package io.shiftleft.fuzzyc2cpg.ast.functionDef;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;

public abstract class FunctionDefBase extends AstNode {

  protected ParameterList parameterList = null;
  protected CompoundStatement content = null;

  public abstract String getName();

  public abstract String getFunctionSignature();

  public ParameterList getParameterList() {
    return this.parameterList;
  }

  public void setParameterList(ParameterList parameterList) {
    this.parameterList = parameterList;
    super.addChild(parameterList);
  }

  public CompoundStatement getContent() {
    return this.content;
  }

  public void setContent(CompoundStatement content) {
    this.content = content;
    super.addChild(content);
  }

  @Override
  public String getEscapedCodeStr() {
    setCodeStr(getFunctionSignature());
    return getCodeStr();
  }
}
