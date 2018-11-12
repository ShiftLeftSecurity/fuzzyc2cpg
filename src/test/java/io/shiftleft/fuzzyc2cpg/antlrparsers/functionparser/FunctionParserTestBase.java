package io.shiftleft.fuzzyc2cpg.antlrparsers.functionparser;


import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriver;
import io.shiftleft.fuzzyc2cpg.parser.functions.AntlrCFunctionParserDriver;

public class FunctionParserTestBase
{
	protected AntlrParserDriver createFunctionDriver()
	{
		AntlrCFunctionParserDriver driver = new AntlrCFunctionParserDriver();

		return driver;
	}

}
