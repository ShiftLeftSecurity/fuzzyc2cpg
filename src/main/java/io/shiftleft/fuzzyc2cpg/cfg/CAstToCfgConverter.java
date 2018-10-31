package io.shiftleft.fuzzyc2cpg.cfg;

import io.shiftleft.fuzzyc2cpg.ast.langc.statements.blockstarters.IfStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.IfStatementBase;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.ASTNodeContainer;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;

public class CAstToCfgConverter extends AstToCfgConverter {

  public void visit(IfStatementBase ifStmt) {
    try {
      CFG block = new CFG();
      IfStatement ifStatement = (IfStatement) ifStmt;

      CfgNode conditionContainer = new ASTNodeContainer(ifStatement.getCondition());
      block.addVertex(conditionContainer);
      block.addEdge(block.getEntryNode(), conditionContainer);

      CFG ifBlock = convert(ifStatement.getStatement());
      block.mountCFG(conditionContainer, block.getExitNode(), ifBlock,
          CFGEdge.TRUE_LABEL);

      if (ifStatement.getElseNode() != null) {
        CFG elseBlock = convert(ifStatement.getElseNode().getStatement());
        block.mountCFG(conditionContainer, block.getExitNode(),
            elseBlock, CFGEdge.FALSE_LABEL);
      } else {
        block.addEdge(conditionContainer, block.getExitNode(),
            CFGEdge.FALSE_LABEL);
      }

      returnCfg = block;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }
}
