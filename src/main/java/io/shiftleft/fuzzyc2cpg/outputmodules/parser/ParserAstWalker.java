package io.shiftleft.fuzzyc2cpg.outputmodules.parser;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTWalker;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public abstract class ParserAstWalker extends ASTWalker {

  protected ASTNodeVisitor astVisitor;

  @Override
  public void startOfUnit(ParserRuleContext ctx, String filename) {
    astVisitor.handleStartOfUnit();
  }

  @Override
  public void endOfUnit(ParserRuleContext ctx, String filename) {
  }

  @Override
  public void processItem(AstNode node, Stack<AstNodeBuilder> nodeStack) {
    node.accept(astVisitor);
  }

}
