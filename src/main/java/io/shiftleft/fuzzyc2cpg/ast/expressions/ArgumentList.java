package io.shiftleft.fuzzyc2cpg.ast.expressions;

import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionHolder;
import java.util.Iterator;
import java.util.LinkedList;

public class ArgumentList extends ExpressionHolder implements Iterable<Expression> {

  private LinkedList<Expression> arguments = new LinkedList<Expression>();

  public int size() {
    return this.arguments.size();
  }

  public Expression getArgument(int index) {
    return this.arguments.get(index);
  }

  public void addArgument(Expression argument) {
    this.arguments.add(argument);
    super.addChild(argument);
  }

  @Override
  public Iterator<Expression> iterator() {
    return this.arguments.iterator();
  }
}
