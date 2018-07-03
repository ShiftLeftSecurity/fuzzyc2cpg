package io.shiftleft.fuzzyc2cpg.outputModules;

import io.shiftleft.fuzzyc2cpg.outputModules.parser.Parser;

public abstract class ParserProtoOutput extends Parser
{

	@Override
	protected void initializeWalker()
	{
		astWalker = new ProtoASTWalker();
	}

	@Override
	protected void initializeDirectoryImporter()
	{

	}

	@Override
	protected void initializeDatabase()
	{

	}

	@Override
	protected void shutdownDatabase()
	{

	}

}
