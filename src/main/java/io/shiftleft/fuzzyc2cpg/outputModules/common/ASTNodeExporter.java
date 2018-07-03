package io.shiftleft.fuzzyc2cpg.outputModules.common;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;

public abstract class ASTNodeExporter
{
	protected Long mainNodeId;

	public long getMainNodeId()
	{
		return mainNodeId;
	}

	public abstract void addToDatabaseSafe(ASTNode node);

}
