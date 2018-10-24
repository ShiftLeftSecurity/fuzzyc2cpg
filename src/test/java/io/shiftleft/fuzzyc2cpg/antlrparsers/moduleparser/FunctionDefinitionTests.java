package io.shiftleft.fuzzyc2cpg.antlrparsers.moduleparser;

import io.shiftleft.fuzzyc2cpg.ModuleLexer;
import io.shiftleft.fuzzyc2cpg.ModuleParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class FunctionDefinitionTests
{

	protected ModuleParser createParser(String input)
	{
		CharStream inputStream = CharStreams.fromString(input);
		ModuleLexer lex = new ModuleLexer(inputStream);
		CommonTokenStream tokens = new CommonTokenStream(lex);
		ModuleParser parser = new ModuleParser(tokens);
		return parser;
	}

}
