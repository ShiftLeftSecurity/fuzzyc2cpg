package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.ast.walking.AstWalker;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class ProtoAstWalker extends AstWalker {

  protected ASTNodeVisitor astVisitor;

  public ProtoAstWalker() {
    astVisitor = new ProtoAstNodeVisitor();
  }

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
