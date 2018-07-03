package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.fuzzyc2cpg.outputmodules.parser.ParserAstWalker;

public class ProtoAstWalker extends ParserAstWalker {

  ProtoAstWalker() {
    astVisitor = new ProtoAstNodeVisitor();
  }
}
