package io.shiftleft.fuzzyc2cpg.outputModules;

import io.shiftleft.fuzzyc2cpg.outputModules.parser.ParserASTWalker;

public class ProtoASTWalker extends ParserASTWalker
{
	ProtoASTWalker()
	{
		astVisitor = new Neo4JASTNodeVisitor();
	}
}
