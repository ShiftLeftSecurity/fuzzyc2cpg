package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.ASTNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTWalker;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class CompoundItemAssembler extends ASTWalker
{

	private CompoundStatement compoundItem;

	public CompoundStatement getCompoundItem()
	{
		return compoundItem;
	}

	@Override
	public void startOfUnit(ParserRuleContext ctx, String filename)
	{
		compoundItem = new CompoundStatement();
	}

	@Override
	public void endOfUnit(ParserRuleContext ctx, String filename)
	{
	}

	@Override
	public void processItem(ASTNode item, Stack<ASTNodeBuilder> itemStack)
	{
		compoundItem.addChild(item);
	}

}
