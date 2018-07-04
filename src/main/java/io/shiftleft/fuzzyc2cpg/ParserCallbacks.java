package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.cfg.ASTToCFGConverter;
import io.shiftleft.fuzzyc2cpg.cfg.CCFGFactory;
import io.shiftleft.fuzzyc2cpg.cfg.CFG;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CFGNode;

public class ParserCallbacks extends ASTNodeVisitor {

  /**
   * Callback triggered for each function definition
   * */

  public void visit(FunctionDefBase ast) {
    ASTToCFGConverter converter = new ASTToCFGConverter();
    converter.setFactory(new CCFGFactory());
    CFG cfg = converter.convert(ast);
    for (CFGNode cfgNode : cfg.getVertices()) {
      // We can check for instanceof AstNodeContainer here
      // and have .astNode.fullName to get the type.
    }

  }

  public void visit(ClassDefStatement node) {

  }

  public void visit(IdentifierDeclStatement node) {

  }

}
