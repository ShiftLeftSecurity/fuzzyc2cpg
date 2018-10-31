package io.shiftleft.fuzzyc2cpg.ast.expressions;

import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class NewExpression extends CallExpressionBase {

  private Expression targetClass = null;

  public Expression getTargetClass() {
    return this.targetClass;
  }

  public void setTargetClass(Expression targetClass) {
    this.targetClass = targetClass;
    super.addChild(targetClass);
  }

  @Override
  public void accept(ASTNodeVisitor visitor) {
    visitor.visit(this);
  }
}
