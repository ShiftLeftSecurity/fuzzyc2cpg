package io.shiftleft.fuzzyc2cpg.cfg;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public class StructuredFlowVisitor extends ASTNodeVisitor
{

	protected CFG returnCFG;

	public CFG getCFG()
	{
		return returnCFG;
	}

	public void visit(CompoundStatement content)
	{
		returnCFG = CFGFactory.newInstance(content);
	}

	public void visit(AstNode expression)
	{
		returnCFG = CFGFactory.newInstance(expression);
	}
	
}
