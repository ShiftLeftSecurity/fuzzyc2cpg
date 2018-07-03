package io.shiftleft.fuzzyc2cpg.ast.expressions;

public class Variable extends Expression {

  private Expression name = null;

  public Expression getNameExpression() {
    return this.name;
  }

  public void setNameExpression(Expression name) {
    this.name = name;
    super.addChild(name);
  }
}
