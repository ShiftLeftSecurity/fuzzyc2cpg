package io.shiftleft.fuzzyc2cpg.ast.walking;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Argument;
import io.shiftleft.fuzzyc2cpg.ast.expressions.AssignmentExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.CallExpressionBase;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Constant;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.expressions.MemberAccess;
import io.shiftleft.fuzzyc2cpg.ast.expressions.PrimaryExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.UnaryExpression;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterBase;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterList;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Condition;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Label;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Statement;
import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.DoStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.ForEachStatement;
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
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Builder;
import java.util.Stack;

public abstract class ASTNodeVisitor {

  public void visit(AstNode item) {
    visitChildren(item);
  }

  public void visit(ParameterList item) {
    defaultHandler(item);
  }

  public void visit(ParameterBase item) {
    defaultHandler(item);
  }

  public void visit(FunctionDefBase item) {
    defaultHandler(item);
  }

  public void visit(ClassDefStatement item) {
    defaultHandler(item);
  }

  public void visit(IdentifierDeclStatement statementItem) {
    defaultHandler(statementItem);
  }

  public void visit(ExpressionStatement statementItem) {
    defaultHandler(statementItem);
  }

  public void visit(CallExpressionBase expression) {
    defaultHandler(expression);
  }

  public void visit(Condition expression) {
    defaultHandler(expression);
  }

  public void visit(AssignmentExpression expression) {
    defaultHandler(expression);
  }

  public void visit(PrimaryExpression expression) {
    defaultHandler(expression);
  }

  public void visit(Constant expression) {
    defaultHandler(expression);
  }

  public void visit(Identifier expression) {
    defaultHandler(expression);
  }

  public void visit(MemberAccess expression) {
    defaultHandler(expression);
  }

  public void visit(UnaryExpression expression) {
    defaultHandler(expression);
  }

  public void visit(Argument expression) {
    defaultHandler(expression);
  }

  public void visit(ReturnStatement expression) {
    defaultHandler(expression);
  }

  public void visit(GotoStatement expression) {
    defaultHandler(expression);
  }

  public void visit(ContinueStatement expression) {
    defaultHandler(expression);
  }

  public void visit(BreakStatement expression) {
    defaultHandler(expression);
  }

  public void visit(CompoundStatement expression) {
    defaultHandler(expression);
  }

  public void visit(IfStatementBase expression) {
    defaultHandler(expression);
  }

  public void visit(ForStatement expression) {
    defaultHandler(expression);
  }

  public void visit(WhileStatement expression) {
    defaultHandler(expression);
  }

  public void visit(DoStatement expression) {
    defaultHandler(expression);
  }

  public void visit(Label expression) {
    defaultHandler(expression);
  }

  public void visit(SwitchStatement expression) {
    defaultHandler(expression);
  }

  public void visit(TryStatement expression) {
    defaultHandler(expression);
  }

  public void visit(ThrowStatement expression) {
    defaultHandler(expression);
  }

  public void visit(ForEachStatement node) {
    defaultHandler(node);
  }

  public void visit(Statement node) {
    defaultHandler(node);
  }

  public void defaultHandler(AstNode item) {
    // by default, redirect to visit(AstNode item)
    visit(item);
  }

  public void visitChildren(AstNode item) {
    int nChildren = item.getChildCount();

    for (int i = 0; i < nChildren; i++) {
      AstNode child = item.getChild(i);
      child.accept(this);
    }

  }
}
