package io.shiftleft.fuzzyc2cpg.ast.declarations;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;

public class IdentifierDeclType extends ASTNode
{
	public String baseType;
	public String completeType;

	public String getEscapedCodeStr()
	{
		return completeType;
	}

}
