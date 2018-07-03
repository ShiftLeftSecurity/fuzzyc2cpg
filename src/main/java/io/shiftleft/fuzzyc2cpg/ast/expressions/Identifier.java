package io.shiftleft.fuzzyc2cpg.ast.expressions;

import io.shiftleft.fuzzyc2cpg.ast.ASTNodeProperties;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class Identifier extends Expression
{
	private StringExpression name = null;
	
	public Identifier()
	{
	}

	public Identifier(Identifier name)
	{
		super(name);
	}

	public String getEnclosingNamespace() {
		return getProperty(ASTNodeProperties.NAMESPACE);
	}
	
	public void setEnclosingNamespace(String namespace) {
		setProperty(ASTNodeProperties.NAMESPACE, namespace);
	}
	
	public void setNameChild(StringExpression name) {
		this.name = name;
		super.addChild(name);
	}
	
	public StringExpression getNameChild() {
		return this.name;
	}

	@Override
	public void accept(ASTNodeVisitor visitor)
	{
		visitor.visit(this);
	}
}
