package io.shiftleft.fuzzyc2cpg.ast.declarations;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;

public class IdentifierDecl extends AstNode {

  private IdentifierDeclType type;
  private Identifier name;

  public void addChild(AstNode node) {
    if (node instanceof Identifier) {
      setName((Identifier) node);
    } else if (node instanceof IdentifierDeclType) {
      setType((IdentifierDeclType) node);
    }

    super.addChild(node);
  }

  public Identifier getName() {
    return name;
  }

  private void setName(Identifier name) {
    this.name = name;
  }

  public IdentifierDeclType getType() {
    return type;
  }

  private void setType(IdentifierDeclType type) {
    this.type = type;
  }

}
