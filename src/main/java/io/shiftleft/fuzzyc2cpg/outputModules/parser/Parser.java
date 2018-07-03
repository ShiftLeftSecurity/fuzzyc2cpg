package io.shiftleft.fuzzyc2cpg.outputModules.parser;

import io.shiftleft.fuzzyc2cpg.fileWalker.SourceFileListener;

public abstract class Parser extends SourceFileListener
{

	protected ParserASTWalker astWalker;

	protected String outputDir;

	protected abstract void initializeDirectoryImporter();

	protected abstract void initializeWalker();

	protected abstract void initializeDatabase();

	protected abstract void shutdownDatabase();

	public void setOutputDir(String anOutputDir)
	{
		outputDir = anOutputDir;
	}

	@Override
	public void initialize()
	{
		initializeDirectoryImporter();
		initializeWalker();
		initializeDatabase();
	}

	@Override
	public void shutdown()
	{
		shutdownDatabase();
	}


}
