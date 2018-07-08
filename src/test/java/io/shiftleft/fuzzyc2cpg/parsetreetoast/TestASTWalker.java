package tests.languages.c.parseTreeToAST;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.walking.AstWalker;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.antlr.v4.runtime.ParserRuleContext;

public class TestASTWalker extends AstWalker
{

	public List<AstNode> codeItems;

	public TestASTWalker()
	{
		codeItems = new LinkedList<>();
	}

	@Override
	public void startOfUnit(ParserRuleContext ctx, String filename)
	{

	}

	@Override
	public void endOfUnit(ParserRuleContext ctx, String filename)
	{

	}

	@Override
	public void processItem(AstNode item, Stack<AstNodeBuilder> itemStack)
	{
		codeItems.add(item);
	}

}
