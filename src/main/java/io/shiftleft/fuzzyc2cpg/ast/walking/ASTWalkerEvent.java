package io.shiftleft.fuzzyc2cpg.ast.walking;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class ASTWalkerEvent {

  public eventID id;

  ;
  public ParserRuleContext ctx;
  public String filename;
  public Stack<AstNodeBuilder> itemStack;
  public AstNode item;
  public ASTWalkerEvent(eventID aId) {
    id = aId;
  }
  public enum eventID {
    BEGIN, START_OF_UNIT, END_OF_UNIT, PROCESS_ITEM, END
  }
}
