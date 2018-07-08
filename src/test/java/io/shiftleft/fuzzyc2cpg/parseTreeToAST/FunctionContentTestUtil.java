package io.shiftleft.fuzzyc2cpg.parseTreeToAST;

import io.shiftleft.fuzzyc2cpg.FunctionLexer;
import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.parser.FunctionParser;
import io.shiftleft.fuzzyc2cpg.parser.TokenSubStream;
import io.shiftleft.fuzzyc2cpg.parser.functions.AntlrCFunctionParserDriver;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class FunctionContentTestUtil
{

	public static AstNode parseAndWalk(String input)
	{
		AntlrCFunctionParserDriver driver = new AntlrCFunctionParserDriver();
		FunctionParser parser = new FunctionParser(driver);

		TokenSubStream tokens = tokenStreamFromString(input);
		parser.parseAndWalkTokenStream(tokens);
		return parser.getParser().builderStack.peek().getItem();
	}

	static ParseTree parse(String input)
	{
		AntlrCFunctionParserDriver driver = new AntlrCFunctionParserDriver();
		FunctionParser parser = new FunctionParser(driver);

		return parser.parseString(input);
	}

	private static TokenSubStream tokenStreamFromString(String input)
	{
		ANTLRInputStream inputStream = new ANTLRInputStream(input);
		FunctionLexer lex = new FunctionLexer(inputStream);
		TokenSubStream tokens = new TokenSubStream(lex);
		return tokens;
	}

}
