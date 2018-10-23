package io.shiftleft.fuzzyc2cpg.ast.walking;

import io.shiftleft.fuzzyc2cpg.ParserCallbacks;
import io.shiftleft.fuzzyc2cpg.StructureCpg;
import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule;
import io.shiftleft.fuzzyc2cpg.outputmodules.ProtoOutputModule;
import java.util.Stack;

import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriverObserver;
import org.antlr.v4.runtime.ParserRuleContext;

public class AstWalker implements AntlrParserDriverObserver {

  protected ASTNodeVisitor callbacks;

  public AstWalker() {
    callbacks = new ParserCallbacks(new ProtoOutputModule());
  }

  public void setOutputModule(OutputModule module) {
    callbacks = new ParserCallbacks(module);
  }

  public void startOfUnit(ParserRuleContext ctx, String filename) {
  }

  public void endOfUnit(ParserRuleContext ctx, String filename) {
  }

  public void processItem(AstNode node, Stack<AstNodeBuilder> nodeStack) {
    node.accept(callbacks);
  }

  public void update(ASTWalkerEvent event) {
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
