package io.shiftleft.fuzzyc2cpg.parser.Functions.builder;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.c.statements.blockstarters.IfStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.DoStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.TryStatement;
import java.util.Stack;

public class ContentBuilderStack
{
	private Stack<ASTNode> itemStack = new Stack<ASTNode>();
	private ShadowStack shadowStack = new ShadowStack(itemStack);

	public void push(ASTNode statementItem)
	{
		shadowStack.push(statementItem);
		itemStack.push(statementItem);
	}

	public ASTNode pop()
	{
		shadowStack.pop();
		return itemStack.pop();
	}

	public int size()
	{
		return itemStack.size();
	}

	public ASTNode peek()
	{
		return itemStack.peek();
	}

	public IfStatement getIfInElseCase()
	{
		return shadowStack.getIfInElseCase();
	}

	public IfStatement getIf()
	{
		return shadowStack.getIf();
	}

	public DoStatement getDo()
	{
		return shadowStack.getDo();
	}

	public TryStatement getTry()
	{
		return shadowStack.getTry();
	}
}
