package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class CompoundItemAssembler implements AntlrParserDriverObserver {

  private CompoundStatement compoundItem;

  public CompoundStatement getCompoundItem() {
    return compoundItem;
  }

  @Override
  public void begin() {

  }

  @Override
  public void end() {

  }

  @Override
  public void startOfUnit(ParserRuleContext ctx, String filename) {
    compoundItem = new CompoundStatement();
  }

  @Override
  public void endOfUnit(ParserRuleContext ctx, String filename) {
  }

  @Override
  public void processItem(AstNode item, Stack<AstNodeBuilder> itemStack) {
    compoundItem.addChild(item);
  }

}
