package io.shiftleft.fuzzyc2cpg.ast.expressions;

import io.shiftleft.fuzzyc2cpg.ast.AstNodeProperties;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class Identifier extends Expression {

  private StringExpression name = null;

  public Identifier() {
  }

  public Identifier(Identifier name) {
    super(name);
  }

  public String getEnclosingNamespace() {
    return getProperty(AstNodeProperties.NAMESPACE);
  }

  public void setEnclosingNamespace(String namespace) {
    setProperty(AstNodeProperties.NAMESPACE, namespace);
  }

  public StringExpression getNameChild() {
    return this.name;
  }

  public void setNameChild(StringExpression name) {
    this.name = name;
    super.addChild(name);
  }

  @Override
  public void accept(ASTNodeVisitor visitor) {
    visitor.visit(this);
  }
}
