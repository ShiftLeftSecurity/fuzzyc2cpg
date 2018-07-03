package io.shiftleft.fuzzyc2cpg.outputModules.common;

import io.shiftleft.fuzzyc2cpg.ast.ASTNode;
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;

public abstract class OutModASTNodeVisitor extends ASTNodeVisitor
{
	protected long importNode(ASTNodeExporter importer, ASTNode node)
	{
		importer.addToDatabaseSafe(node);
		long mainNodeId = importer.getMainNodeId();
		addLinkToClassDef(mainNodeId);
		importer = null;
		return mainNodeId;
	}

	private void addLinkToClassDef(long dstNodeId)
	{
		if (contextStack.size() == 0)
			return;
		Long classId = contextStack.peek();
		addEdgeFromClassToFunc(dstNodeId, classId);
	}

	protected abstract void addEdgeFromClassToFunc(long dstNodeId,
			Long classId);

	protected void visitClassDefContent(ClassDefStatement node,
			long classNodeId)
	{
		// visit compound statement, it might contain
		// functions, declarations or other class definitions
		contextStack.push(classNodeId);
		visit(node.content);
		contextStack.pop();
	}
}
