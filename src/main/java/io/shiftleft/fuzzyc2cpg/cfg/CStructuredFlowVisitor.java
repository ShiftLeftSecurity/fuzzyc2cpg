package io.shiftleft.fuzzyc2cpg.cfg;

import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterBase;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterList;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Label;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.DoStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.ForStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.IfStatementBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.SwitchStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.TryStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.WhileStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.BreakStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ContinueStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.GotoStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ReturnStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ThrowStatement;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.ASTNodeContainer;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;

public class CStructuredFlowVisitor extends StructuredFlowVisitor {

  public void visit(ParameterList paramList)
  {
    returnCFG = CCFGFactory.newInstance(paramList);
  }

  public void visit(ParameterBase param)
  {
    returnCFG = CCFGFactory.newInstance(param);

    for (CfgNode node : returnCFG.getVertices())
    {
      if (!(node instanceof ASTNodeContainer))
        continue;
      returnCFG.registerParameter(node);
    }

  }

  public void visit(ReturnStatement expression)
  {
    returnCFG = CCFGFactory.newInstance(expression);
  }

  public void visit(GotoStatement expression)
  {
    returnCFG = CCFGFactory.newInstance(expression);
  }

  public void visit(IfStatementBase node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  public void visit(ForStatement node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  public void visit(WhileStatement node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  public void visit(DoStatement node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  public void visit(SwitchStatement node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  public void visit(Label node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  public void visit(ContinueStatement expression)
  {
    returnCFG = CCFGFactory.newInstance(expression);
  }

  public void visit(BreakStatement expression)
  {
    returnCFG = CCFGFactory.newInstance(expression);
  }

  public void visit(TryStatement node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }

  @Override
  public void visit(ThrowStatement node)
  {
    returnCFG = CCFGFactory.newInstance(node);
  }


}
