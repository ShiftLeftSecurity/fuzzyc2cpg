package io.shiftleft.fuzzyc2cpg.ast.statements;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Expression;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Statement;

public class ExpressionHolderStatement extends Statement
{
	private Expression expression = null;

	public Expression getExpression()
	{
		return this.expression;
	}
	
	public void setExpression(Expression expression)
	{	
		this.expression = expression;
		super.addChild(expression);
	}
	
	@Override
	public String getEscapedCodeStr()
	{

		Expression expr = getExpression();
		if (expr == null)
			return "";

		setCodeStr(expr.getEscapedCodeStr());
		return getCodeStr();
	}
	
	@Override
	public void addChild(ASTNode node)
	{
		if (node instanceof Expression)
			setExpression((Expression)node);
		else
			super.addChild(node);
	}
}
