package io.shiftleft.fuzzyc2cpg.ast.logical.statements;

import io.shiftleft.fuzzyc2cpg.ast.expressions.IntegerExpression;

public class BreakOrContinueStatement extends JumpStatement {

  private IntegerExpression depth = null;

  public IntegerExpression getDepth() {
    return this.depth;
  }

  public void setDepth(IntegerExpression depth) {
    this.depth = depth;
    super.addChild(depth);
  }

  public Integer getDepthAsInteger() {
    if (this.depth == null) {
      return 0;
    }

    return this.depth.getValue();
  }

  public void decrementDepth() {
    if (this.depth == null) {
      return;
    }

    this.depth.decrement();
  }

}
