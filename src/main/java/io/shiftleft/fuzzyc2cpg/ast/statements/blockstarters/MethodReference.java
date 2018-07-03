package io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.expressions.StringExpression;

public class MethodReference extends ASTNode
{
	private Identifier classIdentifier = null;
	private StringExpression methodName = null;
	
	public Identifier getClassIdentifier()
	{
		return this.classIdentifier;
	}

	public void setClassIdentifier(Identifier classIdentifier)
	{
		this.classIdentifier = classIdentifier;
		super.addChild(classIdentifier);
	}
	
	public StringExpression getMethodName()
	{
		return this.methodName;
	}

	public void setMethodName(StringExpression methodName)
	{
		this.methodName = methodName;
		super.addChild(methodName);
	}
}
