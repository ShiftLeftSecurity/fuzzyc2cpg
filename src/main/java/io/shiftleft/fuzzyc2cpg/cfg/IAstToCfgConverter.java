package io.shiftleft.fuzzyc2cpg.cfg;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;

public interface IAstToCfgConverter {
  CFG convert(AstNode node);
}
