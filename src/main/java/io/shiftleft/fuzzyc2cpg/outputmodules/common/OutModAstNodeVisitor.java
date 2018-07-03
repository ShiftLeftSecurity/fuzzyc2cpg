package io.shiftleft.fuzzyc2cpg.outputmodules.common;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public abstract class OutModAstNodeVisitor extends ASTNodeVisitor {

  protected long importNode(AstNodeExporter importer, AstNode node) {
    importer.addToDatabaseSafe(node);
    long mainNodeId = importer.getMainNodeId();
    addLinkToClassDef(mainNodeId);
    importer = null;
    return mainNodeId;
  }

  private void addLinkToClassDef(long dstNodeId) {
    if (contextStack.size() == 0) {
      return;
    }
    Long classId = contextStack.peek();
    addEdgeFromClassToFunc(dstNodeId, classId);
  }

  protected abstract void addEdgeFromClassToFunc(long dstNodeId,
      Long classId);

  protected void visitClassDefContent(ClassDefStatement node,
      long classNodeId) {
    // visit compound statement, it might contain
    // functions, declarations or other class definitions
    contextStack.push(classNodeId);
    visit(node.content);
    contextStack.pop();
  }
}
