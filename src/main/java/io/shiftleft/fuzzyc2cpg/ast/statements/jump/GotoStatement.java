package io.shiftleft.fuzzyc2cpg.ast.statements.jump;

import io.shiftleft.fuzzyc2cpg.ast.expressions.StringExpression;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.JumpStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class GotoStatement extends JumpStatement {

  private StringExpression label = null;

  public StringExpression getTargetLabel() {
    return this.label;
  }

  public void setTargetLabel(StringExpression label) {
    this.label = label;
    super.addChild(label);
  }

  public String getTargetName() {
    // TODO since C world does not use the setTargetLabel() method but
    // instead uses addChild(), we have to use getChild(0) here
    // instead of getTargetLabel()
    return getChild(0).getEscapedCodeStr();
  }

  public void accept(ASTNodeVisitor visitor) {
    visitor.visit(this);
  }
}
