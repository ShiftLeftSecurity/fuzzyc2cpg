package io.shiftleft.fuzzyc2cpg.ast.c.expressions;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.ArgumentList;
import io.shiftleft.fuzzyc2cpg.ast.expressions.CallExpressionBase;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;

public class CallExpression extends CallExpressionBase
{
	@Override
	public void addChild(ASTNode node)
	{
		if (node instanceof Identifier)
			setTargetFunc((Identifier)node);
		else if (node instanceof ArgumentList)
			setArgumentList((ArgumentList)node);
		else
			super.addChild(node);
	}
}
