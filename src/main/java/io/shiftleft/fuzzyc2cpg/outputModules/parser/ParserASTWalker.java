package io.shiftleft.fuzzyc2cpg.outputModules.parser;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.ASTNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTWalker;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public abstract class ParserASTWalker extends ASTWalker
{

	protected ASTNodeVisitor astVisitor;

	@Override
	public void startOfUnit(ParserRuleContext ctx, String filename)
	{
	}

	@Override
	public void endOfUnit(ParserRuleContext ctx, String filename)
	{
	}

	@Override
	public void processItem(ASTNode node, Stack<ASTNodeBuilder> nodeStack)
	{
		node.accept(astVisitor);
	}

}
