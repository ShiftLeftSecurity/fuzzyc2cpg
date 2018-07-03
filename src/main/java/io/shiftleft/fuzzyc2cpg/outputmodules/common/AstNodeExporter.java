package io.shiftleft.fuzzyc2cpg.outputmodules.common;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;

public abstract class AstNodeExporter {

  protected Long mainNodeId;

  public long getMainNodeId() {
    return mainNodeId;
  }

  public abstract void addToDatabaseSafe(AstNode node);

}
