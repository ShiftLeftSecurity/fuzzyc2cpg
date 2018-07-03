package io.shiftleft.fuzzyc2cpg.cfg.nodes;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;

public class ASTNodeContainer extends AbstractCFGNode
{

	private AstNode astNode;

	public ASTNodeContainer(AstNode node)
	{
		if (node == null)
		{
			throw new IllegalArgumentException("node must not be null");
		}
		setASTNode(node);
	}

	private void setASTNode(AstNode astNode)
	{
		this.astNode = astNode;
		this.astNode.markAsCFGNode();
	}

	public AstNode getASTNode()
	{
		return astNode;
	}

	public String getEscapedCodeStr()
	{
		if (getASTNode() == null)
			return "";

		return getASTNode().getEscapedCodeStr();
	}

	public void markAsCFGNode()
	{
		if (getASTNode() == null)
			return;
		getASTNode().markAsCFGNode();
	}

	@Override
	public String toString()
	{
		return this.astNode.toString();
	}

}
