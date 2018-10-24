package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Stack;

public interface AntlrParserDriverObserver {
  void begin();

  void end();

  void startOfUnit(ParserRuleContext ctx, String filename);

  void endOfUnit(ParserRuleContext ctx, String filename);

  void processItem(AstNode node, Stack<AstNodeBuilder> builderStack);
}
