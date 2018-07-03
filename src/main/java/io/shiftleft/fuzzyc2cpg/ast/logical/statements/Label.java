package io.shiftleft.fuzzyc2cpg.ast.logical.statements;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.StringExpression;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class Label extends Statement
{
	private StringExpression name = null;

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

	public String getLabelName()
	{
		if(getNameChild() != null)
			return getNameChild().getEscapedCodeStr();

		String codeStr = getEscapedCodeStr();
		return codeStr.substring(0, codeStr.length() - 2);
	}
	
	@Override
	public void addChild(ASTNode node)
	{
		if (node instanceof StringExpression)
			setNameChild((StringExpression) node);
		else
			super.addChild(node);
	}
}
