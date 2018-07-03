package io.shiftleft.fuzzyc2cpg.parser.Shared.builders;

import io.shiftleft.fuzzyc2cpg.ModuleParser.Class_nameContext;
import io.shiftleft.fuzzyc2cpg.FunctionParser;
import io.shiftleft.fuzzyc2cpg.ast.ASTNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.parser.ASTNodeFactory;
import org.antlr.v4.runtime.ParserRuleContext;

public class ClassDefBuilder extends ASTNodeBuilder
{
	ClassDefStatement thisItem;

	@Override
	public void createNew(ParserRuleContext ctx)
	{
		item = new ClassDefStatement();
		thisItem = (ClassDefStatement) item;
		ASTNodeFactory.initializeFromContext(thisItem, ctx);
	}

	// TODO: merge the following two by introducing a wrapper
	public void setName(Class_nameContext ctx)
	{
		thisItem.identifier = new Identifier();
		ASTNodeFactory.initializeFromContext(thisItem.identifier, ctx);
	}

	public void setName(FunctionParser.Class_nameContext ctx)
	{
		thisItem.identifier = new Identifier();
		ASTNodeFactory.initializeFromContext(thisItem.identifier, ctx);
	}

	public void setContent(CompoundStatement content)
	{
		thisItem.content = content;
	}

}
