package io.shiftleft.fuzzyc2cpg.ast.logical.statements;

import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionHolder;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class Condition extends ExpressionHolder
{
	public void accept(ASTNodeVisitor visitor)
	{
		visitor.visit(this);
	}
}
