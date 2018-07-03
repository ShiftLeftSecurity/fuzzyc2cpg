package io.shiftleft.fuzzyc2cpg.outputModules;

import io.shiftleft.fuzzyc2cpg.outputModules.parser.Parser;
import java.util.Map;

public abstract class ParserProtoOutput extends Parser
{

	@Override
	protected void initializeWalker()
	{
		astWalker = null;
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
