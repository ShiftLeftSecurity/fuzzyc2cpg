package io.shiftleft.fuzzyc2cpg.ast.walking;

import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;

public class FunctionNodeVisitor extends ASTNodeVisitor
{
	ASTNodeVisitor functionNodeVisitor;

	public void setFunctionNodeVisitor(ASTNodeVisitor aNodeVisitor)
	{
		functionNodeVisitor = aNodeVisitor;
	}

	public ASTNodeVisitor getFunctionNodeVisitor()
	{
		return functionNodeVisitor;
	}

	public void visit(FunctionDefBase node)
	{
		node.accept(functionNodeVisitor);
	}

	public void visit(IdentifierDeclStatement statementItem)
	{
		// don't expand identifier declarations
	}

}
