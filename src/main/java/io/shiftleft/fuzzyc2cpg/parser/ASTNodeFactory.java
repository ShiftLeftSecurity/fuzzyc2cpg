package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.FunctionParser.InitDeclWithAssignContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Parameter_nameContext;
import io.shiftleft.fuzzyc2cpg.FunctionParser.StatementContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Parameter_declContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Parameter_idContext;
import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.c.functionDef.Parameter;
import io.shiftleft.fuzzyc2cpg.ast.c.functionDef.ParameterType;
import io.shiftleft.fuzzyc2cpg.ast.expressions.AssignmentExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.BinaryExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Expression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Statement;
import org.antlr.v4.runtime.ParserRuleContext;

public class ASTNodeFactory
{
	public static void initializeFromContext(ASTNode node,
			ParserRuleContext ctx)
	{
		if (ctx == null)
			return;
		node.setLocation(CodeLocationExtractor.extractFromContext(ctx));
		node.setCodeStr(escapeCodeStr(ParseTreeUtils.childTokenString(ctx)));
	}

	public static void initializeFromContext(Expression node,
			ParserRuleContext ctx)
	{
		initializeFromContext((ASTNode) node, ctx);
		if (node instanceof BinaryExpression && ctx.getChildCount() == 3)
			node.setOperator(ctx.getChild(1).getText());
	}

	public static ASTNode create(StatementContext ctx)
	{
		ASTNode node = new Statement();
		initializeFromContext(node, ctx);
		return node;
	}

	private static String escapeCodeStr(String codeStr)
	{
		String retval = codeStr;
		retval = retval.replace("\n", "\\n");
		retval = retval.replace("\t", "\\t");
		return retval;
	}

	public static AssignmentExpression create(InitDeclWithAssignContext ctx)
	{
		AssignmentExpression assign = new AssignmentExpression();
		initializeFromContext(assign, ctx);
		if (ctx.getChildCount() == 3)
			assign.setOperator(ctx.getChild(1).getText());

		return assign;
	}

	public static Parameter create(Parameter_declContext ctx)
	{
		Parameter param = new Parameter();

		Parameter_declContext paramCtx = ctx;
		Parameter_nameContext paramName = getNameOfParameter(paramCtx);

		Identifier name = new Identifier();
		ParameterType type = new ParameterType();
		initializeFromContext(type, ctx);
		initializeFromContext(name, paramName);
		initializeFromContext(param, ctx);

		param.addChild(type);
		param.addChild(name);

		return param;
	}

	private static Parameter_nameContext getNameOfParameter(
			Parameter_declContext param_ctx)
	{
		Parameter_idContext parameter_id = param_ctx.parameter_id();

		while (parameter_id.parameter_name() == null)
		{
			parameter_id = parameter_id.parameter_id();
		}
		return parameter_id.parameter_name();
	}

}
