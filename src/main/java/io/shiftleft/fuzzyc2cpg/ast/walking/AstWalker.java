package io.shiftleft.fuzzyc2cpg.ast.walking;

import io.shiftleft.fuzzyc2cpg.ParserCallbacks;
import io.shiftleft.fuzzyc2cpg.StructureCpg;
import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class AstWalker implements Observer {

  protected ASTNodeVisitor callbacks;

  public AstWalker() {
    callbacks = new ParserCallbacks();
  }

  public void startOfUnit(ParserRuleContext ctx, String filename) {
    callbacks.handleStartOfUnit();
  }

  public void endOfUnit(ParserRuleContext ctx, String filename) {
  }

  public void processItem(AstNode node, Stack<AstNodeBuilder> nodeStack) {
    node.accept(callbacks);
  }

  public void update(Observable obj, Object arg) {
    ASTWalkerEvent event = (ASTWalkerEvent) arg;
    switch (event.id) {
      case BEGIN:
        begin();
        break;
      case START_OF_UNIT:
        startOfUnit(event.ctx, event.filename);
        break;
      case END_OF_UNIT:
        endOfUnit(event.ctx, event.filename);
        break;
      case PROCESS_ITEM:
        processItem(event.item, event.itemStack);
        break;
      case END:
        end();
        break;
    }
  }

  public void begin() {
  }

  public void end() {
  }

  public void setStructureCpg(StructureCpg structureCpg) {
    callbacks.setStructureCpg(structureCpg);
  }
}
