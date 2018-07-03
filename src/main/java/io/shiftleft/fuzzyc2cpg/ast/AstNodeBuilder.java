package io.shiftleft.fuzzyc2cpg.ast;

import org.antlr.v4.runtime.ParserRuleContext;

abstract public class AstNodeBuilder {

  protected AstNode item;

  public AstNode getItem() {
    return item;
  }

  abstract public void createNew(ParserRuleContext ctx);

}
