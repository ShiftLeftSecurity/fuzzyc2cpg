package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.outputModules.ParserProtoOutput;
import io.shiftleft.fuzzyc2cpg.parser.Modules.ANTLRCModuleParserDriver;
import io.shiftleft.fuzzyc2cpg.parser.ModuleParser;
import java.nio.file.Path;

class CParserProtoOuput extends ParserProtoOutput
{

	ANTLRCModuleParserDriver driver = new ANTLRCModuleParserDriver();
	ModuleParser parser = new ModuleParser(driver);

	@Override
	public void visitFile(Path pathToFile)
	{
		parser.parseFile(pathToFile.toString());
	}

	@Override
	public void preVisitDirectory(Path dir) {

	}

	@Override
	public void postVisitDirectory(Path dir) {

	}

	@Override
	public void initialize()
	{
		super.initialize();
		parser.addObserver(astWalker);
	}

}