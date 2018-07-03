package io.shiftleft.fuzzyc2cpg.ast.expressions;

public class Constant extends Expression {

  private Identifier identifier = null;

  public Identifier getIdentifier() {
    return this.identifier;
  }

  public void setIdentifier(Identifier identifier) {
    this.identifier = identifier;
    super.addChild(identifier);
  }
}
