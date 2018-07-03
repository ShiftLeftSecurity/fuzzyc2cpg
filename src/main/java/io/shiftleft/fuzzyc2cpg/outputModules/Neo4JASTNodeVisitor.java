package io.shiftleft.fuzzyc2cpg.outputModules;


// Stays alive during the lifetime of the program

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.outputModules.common.ASTNodeExporter;
import io.shiftleft.fuzzyc2cpg.outputModules.common.OutModASTNodeVisitor;

public class Neo4JASTNodeVisitor extends OutModASTNodeVisitor
{

	public void visit(FunctionDefBase node)
	{

	}

	public void visit(ClassDefStatement node)
	{

	}

	public void visit(IdentifierDeclStatement node)
	{

	}

	@Override
	protected void addEdgeFromClassToFunc(long dstNodeId, Long classId)
	{

	}

}
