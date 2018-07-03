package io.shiftleft.fuzzyc2cpg.parser.Modules.builder;

import io.shiftleft.fuzzyc2cpg.ModuleParser.Function_nameContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Function_param_listContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Parameter_declContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Return_typeContext;
import io.shiftleft.fuzzyc2cpg.parser.ASTNodeFactory;
import io.shiftleft.fuzzyc2cpg.parser.Functions.builder.ParameterListBuilder;
import io.shiftleft.fuzzyc2cpg.parser.ParseTreeUtils;
import java.util.Stack;

import org.antlr.v4.runtime.ParserRuleContext;


import io.shiftleft.fuzzyc2cpg.ast.ASTNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.c.functionDef.FunctionDef;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ReturnType;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;

public class FunctionDefBuilder extends ASTNodeBuilder
{

	FunctionDef thisItem;
	ParameterListBuilder paramListBuilder = new ParameterListBuilder();

	@Override
	public void createNew(ParserRuleContext ctx)
	{
		item = new FunctionDef();
		ASTNodeFactory.initializeFromContext(item, ctx);
		thisItem = (FunctionDef) item;
	}

	public void setName(Function_nameContext ctx,
			Stack<ASTNodeBuilder> itemStack)
	{
		thisItem.addChild(new Identifier());
		ASTNodeFactory.initializeFromContext(thisItem.getIdentifier(), ctx);
	}

	public void setReturnType(Return_typeContext ctx,
			Stack<ASTNodeBuilder> itemStack)
	{
		ReturnType returnType = new ReturnType();
		ASTNodeFactory.initializeFromContext(returnType, ctx);
		returnType
				.setBaseType(ParseTreeUtils.childTokenString(ctx.type_name()));
		returnType.setCompleteType(ParseTreeUtils.childTokenString(ctx));
		thisItem.addChild(returnType);
	}

	public void setParameterList(Function_param_listContext ctx,
			Stack<ASTNodeBuilder> itemStack)
	{
		paramListBuilder.createNew(ctx);
		thisItem.addChild(paramListBuilder.getItem());
	}

	public void addParameter(Parameter_declContext ctx,
			Stack<ASTNodeBuilder> itemStack)
	{
		paramListBuilder.addParameter(ctx, itemStack);
	}

	public void setContent(CompoundStatement functionContent)
	{
		thisItem.addChild(functionContent);
	}

}
