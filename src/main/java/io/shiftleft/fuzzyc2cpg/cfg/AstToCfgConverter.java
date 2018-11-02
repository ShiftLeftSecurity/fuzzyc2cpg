package io.shiftleft.fuzzyc2cpg.cfg;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.declarations.IdentifierDecl;
import io.shiftleft.fuzzyc2cpg.ast.expressions.AssignmentExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.BinaryExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Condition;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Constant;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Expression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterList;
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression;
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.BreakOrContinueStatement;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Label;
import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.CatchStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.DoStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.ForStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.SwitchStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.TryStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.WhileStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.BreakStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ContinueStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.GotoStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ReturnStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.jump.ThrowStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.ASTNodeContainer;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgEntryNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgErrorNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgExceptionNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgExitNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.InfiniteForNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AstToCfgConverter implements ASTNodeVisitor, IAstToCfgConverter {
  protected CFG returnCfg;

  @Override
  public CFG convert(AstNode node) {
    node.accept(this);
    return returnCfg;
  }

  @Override
  public void visit(FunctionDefBase functionDefinition) {
    try {
      CFG function = newPassThroughCFG();
      CFG parameterBlock = convert(
          functionDefinition.getParameterList());
      CFG functionBody = convert(functionDefinition.getContent());
      parameterBlock.appendCFG(functionBody);
      function.appendCFG(parameterBlock);
      fixGotoStatements(function);
      fixReturnStatements(function);
      if (!function.getBreakStatements().isEmpty()) {
        System.err.println("warning: unresolved break statement");
        fixBreakStatements(function, function.getErrorNode());
      }
      if (!function.getContinueStatements().isEmpty()) {
        System.err.println("warning: unresolved continue statement");
        fixContinueStatement(function, function.getErrorNode());
      }
      if (function.hasExceptionNode()) {
        function.addEdge(function.getExceptionNode(),
            function.getExitNode(), CFGEdge.UNHANDLED_EXCEPT_LABEL);
      }

      returnCfg = function;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(ParameterList parameterList) {
    try {
      CFG block = newPassThroughCFG();

      parameterList.getChildIterator().forEachRemaining(parameter -> {
        CFG parameterCfg = convert(parameter);
        block.appendCFG(parameterCfg);
      });

      returnCfg = block;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(Parameter parameter) {
    returnCfg = newPassThroughCFG();
  }

  @Override
  public void visit(IdentifierDeclStatement identifierDeclStatement) {
    try {
      CFG block = newPassThroughCFG();

      for (AstNode identifierDecl : identifierDeclStatement.getIdentifierDeclList()) {
        CFG identifierDeclCfg = convert(identifierDecl);
        block.appendCFG(identifierDeclCfg);
      }

      returnCfg = block;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(IdentifierDecl identifierDecl) {
    AssignmentExpression assignment = identifierDecl.getAssignment();
    if (assignment != null) {
      assignment.accept(this);
    } else {
      returnCfg = newPassThroughCFG();
    }
  }

  @Override
  public void visit(Identifier identifier) {
    returnCfg = newSingleNodeCfg(identifier);
  }

  @Override
  public void visit(Constant constant) {
    returnCfg = newSingleNodeCfg(constant);
  }

  @Override
  public void visit(Condition condition) {
    condition.getExpression().accept(this);
  }

  @Override
  public void visit(ExpressionStatement expressionStatement) {
    expressionStatement.getExpression().accept(this);
  }

  @Override
  public void visit(CallExpression callExpression) {
    try {
      CFG block = newPassThroughCFG();

      for (Expression argument : callExpression.getArgumentList()) {
        CFG argumentCfg = convert(argument);
        block.appendCFG(argumentCfg);
      }

      CfgNode container = new ASTNodeContainer(callExpression);
      block.appendCFGNode(container);

      returnCfg = block;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  // TODO This also handles || and && for which we do not correctly model the lazyness.
  // Fix ones clear how to represent in CFG.
  @Override
  public void visit(BinaryExpression binaryExpression) {
    try {
      CFG block = newPassThroughCFG();

      CFG leftArgumentCfg = convert(binaryExpression.getLeft());
      CFG rightArgumentCfg = convert(binaryExpression.getRight());

      block.appendCFG(leftArgumentCfg);
      block.appendCFG(rightArgumentCfg);

      CfgNode container = new ASTNodeContainer(binaryExpression);
      block.appendCFGNode(container);

      returnCfg = block;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(WhileStatement whileStatement) {

    try {
      CFG whileCfg = convert(whileStatement.getCondition());
      CFG whileBody = convert(whileStatement.getStatement());

      whileCfg.mountCFGAtExit(whileCfg.getEntryNode(),
          whileBody, CFGEdge.TRUE_LABEL);
      whileCfg.addEdge(whileCfg.getExitNode(), whileBlock.getExitNode(),
          CFGEdge.FALSE_LABEL);

      fixBreakStatements(whileBlock, whileBlock.getExitNode());
      fixContinueStatement(whileBlock, conditionContainer);

      returnCfg = whileBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }


    try {
      CFG whileBlock = new CFG();
      CfgNode conditionContainer = new ASTNodeContainer(
          whileStatement.getCondition());
      whileBlock.addVertex(conditionContainer);
      whileBlock.addEdge(whileBlock.getEntryNode(), conditionContainer);

      CFG whileBody = convert(whileStatement.getStatement());

      whileBlock.mountCFG(conditionContainer, conditionContainer,
          whileBody, CFGEdge.TRUE_LABEL);
      whileBlock.addEdge(conditionContainer, whileBlock.getExitNode(),
          CFGEdge.FALSE_LABEL);

      fixBreakStatements(whileBlock, whileBlock.getExitNode());
      fixContinueStatement(whileBlock, conditionContainer);

      returnCfg = whileBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(ForStatement forStatement) {
    try {
      CFG forBlock = new CFG();

      AstNode initialization = forStatement.getForInitExpression();
      AstNode condition = forStatement.getCondition();
      AstNode expression = forStatement.getForLoopExpression();

      CFG forBody = convert(forStatement.getStatement());
      CfgNode conditionContainer;

      if (condition != null) {
        conditionContainer = new ASTNodeContainer(condition);
      } else {
        conditionContainer = new InfiniteForNode();
      }

      forBlock.addVertex(conditionContainer);
      forBlock.addEdge(conditionContainer, forBlock.getExitNode(),
          CFGEdge.FALSE_LABEL);

      if (initialization != null) {
        CfgNode initializationContainer = new ASTNodeContainer(
            initialization);
        forBlock.addVertex(initializationContainer);
        forBlock.addEdge(forBlock.getEntryNode(),
            initializationContainer);
        forBlock.addEdge(initializationContainer, conditionContainer);
      } else {
        forBlock.addEdge(forBlock.getEntryNode(), conditionContainer);
      }

      if (expression != null) {
        CfgNode expressionContainer = new ASTNodeContainer(expression);
        forBlock.addVertex(expressionContainer);
        forBlock.addEdge(expressionContainer, conditionContainer);
        forBlock.mountCFG(conditionContainer, expressionContainer,
            forBody, CFGEdge.TRUE_LABEL);
      } else {
        forBlock.mountCFG(conditionContainer, conditionContainer,
            forBody, CFGEdge.TRUE_LABEL);
      }

      fixBreakStatements(forBlock, forBlock.getExitNode());
      fixContinueStatement(forBlock, conditionContainer);

      returnCfg = forBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(DoStatement doStatement) {
    try {
      CFG doBlock = new CFG();

      CfgNode conditionContainer = new ASTNodeContainer(
          doStatement.getCondition());

      doBlock.addVertex(conditionContainer);
      doBlock.addEdge(conditionContainer, doBlock.getExitNode(),
          CFGEdge.FALSE_LABEL);

      CFG doBody = convert(doStatement.getStatement());

      doBlock.mountCFG(doBlock.getEntryNode(), conditionContainer, doBody,
          CFGEdge.EMPTY_LABEL);

      int nVerticesOfBody = doBody.getVertices().size();
      if (nVerticesOfBody == 2) {
        doBlock.addEdge(conditionContainer, conditionContainer,
            CFGEdge.TRUE_LABEL);
      } else {
        for (CFGEdge edge : doBody.outgoingEdges(doBody.getEntryNode())) {
          doBlock.addEdge(conditionContainer, edge.getDestination(),
              CFGEdge.TRUE_LABEL);
        }
      }

      fixBreakStatements(doBlock, doBlock.getExitNode());
      fixContinueStatement(doBlock, conditionContainer);

      returnCfg = doBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(TryStatement tryStatement) {
    try {
      CFG tryCFG = convert(tryStatement.getStatement());
      List<CfgNode> statements = new ArrayList<CfgNode>();

      // Get all nodes within try not connected to an exception node.
      for (CfgNode node : tryCFG.getVertices()) {
        if (!(node instanceof CfgEntryNode) && !(node instanceof CfgExitNode)) {
          boolean b = true;
          for (CFGEdge edge : tryCFG.outgoingEdges(node)) {
            CfgNode destination = edge.getDestination();
            if (destination instanceof CfgExceptionNode) {
              b = false;
              break;
            }
          }
          if (b) {
            statements.add(node);
          }
        }
      }

      // Add exception node for current try block
      if (!statements.isEmpty()) {
        CfgExceptionNode exceptionNode = new CfgExceptionNode();
        tryCFG.setExceptionNode(exceptionNode);
        for (CfgNode node : statements) {
          tryCFG.addEdge(node, exceptionNode, CFGEdge.EXCEPT_LABEL);
        }
      }

      if (tryStatement.getCatchList().size() == 0) {
        System.err.println("warning: cannot find catch for try");
        returnCfg = tryCFG;
      }

      // Mount exception handlers
      for (CatchStatement catchStatement : tryStatement.getCatchList()) {
        CFG catchBlock = convert(catchStatement.getStatement());
        tryCFG.mountCFG(tryCFG.getExceptionNode(), tryCFG.getExitNode(),
            catchBlock, CFGEdge.HANDLED_EXCEPT_LABEL);
      }

      returnCfg = tryCFG;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(SwitchStatement switchStatement) {
    try {
      CFG switchBlock = new CFG();
      CfgNode conditionContainer = new ASTNodeContainer(
          switchStatement.getCondition());
      switchBlock.addVertex(conditionContainer);
      switchBlock.addEdge(switchBlock.getEntryNode(), conditionContainer);

      CFG switchBody = convert(switchStatement.getStatement());

      switchBlock.addCFG(switchBody);

      boolean defaultLabel = false;

      HashMap<String, CfgNode> nonCaseLabels = new HashMap<>();
      for (Map.Entry<String, CfgNode> block : switchBody.getLabels().entrySet()) {
        // Skip labels that aren't for switch statements.
        if (!block.getKey().matches("^(case|default).*")) {
          nonCaseLabels.put(block.getKey(), block.getValue());
          continue;
        }

        if (block.getKey().equals("default")) {
          defaultLabel = true;
        }
        switchBlock.addEdge(conditionContainer, block.getValue(),
            block.getKey());
      }


      // Hide case/default labels from upstream CFG analysis, they can't
      // reference internal labels anyway and this prevents bugs with
      // nested switch statements where the parent switch statement
      // references the childs labels.
      switchBlock.setLabels(nonCaseLabels);

      for (CFGEdge edge : switchBody.incomingEdges(switchBody
          .getExitNode())) {
        switchBlock.addEdge(edge.getSource(),
            switchBlock.getExitNode());
      }
      if (!defaultLabel) {
        switchBlock.addEdge(conditionContainer,
            switchBlock.getExitNode());
      }

      fixBreakStatements(switchBlock, switchBlock.getExitNode());

      returnCfg = switchBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(CompoundStatement content) {
    try {
      CFG compoundBlock = newPassThroughCFG();
      for (AstNode statement : content.getStatements()) {
        compoundBlock.appendCFG(convert(statement));
      }
      returnCfg = compoundBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(ReturnStatement returnStatement) {
    try {
      CFG returnBlock = new CFG();
      CfgNode returnContainer = new ASTNodeContainer(returnStatement);
      returnBlock.addVertex(returnContainer);
      returnBlock.addEdge(returnBlock.getEntryNode(), returnContainer);
      returnBlock.addEdge(returnContainer, returnBlock.getExitNode());
      returnBlock.addReturnStatement(returnContainer);
      returnCfg = returnBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(GotoStatement gotoStatement) {
    try {
      CFG gotoBlock = new CFG();
      CfgNode gotoContainer = new ASTNodeContainer(gotoStatement);
      gotoBlock.addVertex(gotoContainer);
      gotoBlock.addEdge(gotoBlock.getEntryNode(), gotoContainer);
      gotoBlock.addEdge(gotoContainer, gotoBlock.getExitNode());
      gotoBlock.addGotoStatement(gotoContainer,
          gotoStatement.getTargetName());
      returnCfg = gotoBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(Label labelStatement) {
    try {
      CFG continueBlock = new CFG();
      CfgNode labelContainer = new ASTNodeContainer(labelStatement);
      continueBlock.addVertex(labelContainer);
      continueBlock.addEdge(continueBlock.getEntryNode(), labelContainer);
      continueBlock.addEdge(labelContainer, continueBlock.getExitNode());
      String label = labelStatement.getLabelName();
      continueBlock.addBlockLabel(label, labelContainer);
      returnCfg = continueBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(ContinueStatement continueStatement) {
    try {
      CFG continueBlock = new CFG();
      CfgNode continueContainer = new ASTNodeContainer(continueStatement);
      continueBlock.addVertex(continueContainer);
      continueBlock.addEdge(continueBlock.getEntryNode(),
          continueContainer);
      continueBlock.addEdge(continueContainer,
          continueBlock.getExitNode());
      continueBlock.addContinueStatement(continueContainer);
      returnCfg = continueBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(BreakStatement breakStatement) {
    try {
      CFG breakBlock = new CFG();
      CfgNode breakContainer = new ASTNodeContainer(breakStatement);
      breakBlock.addVertex(breakContainer);
      breakBlock.addEdge(breakBlock.getEntryNode(), breakContainer);
      breakBlock.addEdge(breakContainer, breakBlock.getExitNode());
      breakBlock.addBreakStatement(breakContainer);
      returnCfg = breakBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  @Override
  public void visit(ThrowStatement throwStatement) {
    try {
      CFG throwBlock = new CFG();
      CfgNode throwContainer = new ASTNodeContainer(throwStatement);
      CfgExceptionNode exceptionNode = new CfgExceptionNode();
      throwBlock.addVertex(throwContainer);
      throwBlock.setExceptionNode(exceptionNode);
      throwBlock.addEdge(throwBlock.getEntryNode(), throwContainer);
      throwBlock.addEdge(throwContainer, exceptionNode,
          CFGEdge.EXCEPT_LABEL);
      // throwBlock.addEdge(throwContainer, throwBlock.getExitNode());
      returnCfg = throwBlock;
    } catch (RuntimeException exception) {
      returnCfg = newErrorInstance(exception);
    }
  }

  private CFG newPassThroughCFG() {
    CFG cfg = new CFG();
    cfg.addEdge(cfg.getEntryNode(), cfg.getExitNode());
    return cfg;
  }

  private CFG newSingleNodeCfg(AstNode node) {
    CFG block = new CFG();
    CfgNode container = new ASTNodeContainer(node);
    block.addVertex(container);
    block.addEdge(block.getEntryNode(), container);
    block.addEdge(container, block.getExitNode());
    return block;
  }

  private void fixGotoStatements(CFG thisCFG) {
    for (Map.Entry<CfgNode, String> entry : thisCFG.getGotoStatements()
        .entrySet()) {
      CfgNode gotoStatement = entry.getKey();
      String label = entry.getValue();
      thisCFG.removeEdgesFrom(gotoStatement);
      thisCFG.addEdge(gotoStatement, thisCFG.getBlockByLabel(label));
    }
    thisCFG.getGotoStatements().clear();
  }

  private void fixReturnStatements(CFG thisCFG) {
    for (CfgNode returnStatement : thisCFG.getReturnStatements()) {
      thisCFG.removeEdgesFrom(returnStatement);
      thisCFG.addEdge(returnStatement, thisCFG.getExitNode());
    }
    thisCFG.getReturnStatements().clear();
  }

  private void fixBreakStatements(CFG thisCFG, CfgNode target) {
    List<CfgNode> breakStatements = thisCFG.getBreakStatements();
    Iterator<CfgNode> it = breakStatements.iterator();

    fixBreakOrContinueStatements(thisCFG, target, it);
  }

  private void fixBreakOrContinueStatements(CFG thisCFG, CfgNode target, Iterator<CfgNode> it) {
    while (it.hasNext()) {
      CfgNode breakOrContinueNode = it.next();

      thisCFG.removeEdgesFrom(breakOrContinueNode);
      thisCFG.addEdge(breakOrContinueNode, target);
      it.remove();
    }
  }

  private void fixContinueStatement(CFG thisCFG, CfgNode target) {
    List<CfgNode> continueStatements = thisCFG.getContinueStatements();
    Iterator<CfgNode> it = continueStatements.iterator();

    fixBreakOrContinueStatements(thisCFG, target, it);
  }

  protected CFG newErrorInstance(RuntimeException exception) {
    if (true) {
      CFG errorBlock = new CFG();
      CfgNode errorNode = new CfgErrorNode();
      errorBlock.addVertex(errorNode);
      errorBlock.addEdge(errorBlock.getEntryNode(), errorNode);
      errorBlock.addEdge(errorNode, errorBlock.getExitNode());
      return errorBlock;
    } else {
      throw exception;
    }
  }
}
