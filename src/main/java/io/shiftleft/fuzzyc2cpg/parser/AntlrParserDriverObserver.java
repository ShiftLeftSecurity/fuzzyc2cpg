package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.walking.ASTWalkerEvent;

public interface AntlrParserDriverObserver {
  void update(ASTWalkerEvent event);
}
