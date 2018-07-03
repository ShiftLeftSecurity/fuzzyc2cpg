package io.shiftleft.fuzzyc2cpg.ast.expressions;

import java.util.Iterator;
import java.util.LinkedList;

public class ExpressionList extends Expression implements Iterable<Expression> {

  private LinkedList<Expression> expressions = new LinkedList<Expression>();

  public int size() {
    return this.expressions.size();
  }

  public Expression getExpression(int index) {
    return this.expressions.get(index);
  }

  public void addExpression(Expression expression) {
    this.expressions.add(expression);
    super.addChild(expression);
  }

  @Override
  public Iterator<Expression> iterator() {
    return this.expressions.iterator();
  }
}
