package io.shiftleft.fuzzyc2cpg.ast.c.functionDef;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterBase;

public class Parameter extends ParameterBase
{
	private ParameterType type = new ParameterType();
	private Identifier identifier = new Identifier();

	public ParameterType getType()
	{
		return type;
	}

	public void setType(ASTNode type)
	{
		if( type instanceof ParameterType)
			this.type = (ParameterType)type;
		super.addChild(type);
	}
	
	@Override
	public String getName() {
		return this.getIdentifier().getEscapedCodeStr();
	}
	
	public Identifier getIdentifier()
	{
		return this.identifier;
	}

	private void setIdentifier(Identifier identifier)
	{
		this.identifier = identifier;
		super.addChild(identifier);
	}
	
	public void addChild(ASTNode node)
	{
		if (node instanceof ParameterType)
			setType((ParameterType) node);
		else if (node instanceof Identifier)
			setIdentifier((Identifier) node);
		else
			super.addChild(node);
	}
}
