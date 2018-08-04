package io.shiftleft.fuzzyc2cpg.cfg;

import io.shiftleft.fuzzyc2cpg.ast.langc.statements.blockstarters.IfStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.blockstarters.IfStatementBase;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.ASTNodeContainer;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;

public class CCFGFactory extends CFGFactory
{

	static{
		structuredFlowVisitor = new CStructuredFlowVisitor();
	}

	public CCFGFactory()
	{
		structuredFlowVisitor = new CStructuredFlowVisitor();
	}

	public static CFG newInstance(IfStatementBase ifStmt)
	{
		try
		{
			IfStatement ifStatement = (IfStatement)ifStmt;

			CFG block = new CFG();
			CfgNode conditionContainer = new ASTNodeContainer(
					ifStatement.getCondition());
			block.addVertex(conditionContainer);
			block.addEdge(block.getEntryNode(), conditionContainer);

			CFG ifBlock = convert(ifStatement.getStatement());
			block.mountCFG(conditionContainer, block.getExitNode(), ifBlock,
					CFGEdge.TRUE_LABEL);

			if (ifStatement.getElseNode() != null)
			{
				CFG elseBlock = convert(
						ifStatement.getElseNode().getStatement());
				block.mountCFG(conditionContainer, block.getExitNode(),
						elseBlock, CFGEdge.FALSE_LABEL);
			}
			else
			{
				block.addEdge(conditionContainer, block.getExitNode(),
						CFGEdge.FALSE_LABEL);
			}

			return block;
		}
		catch (Exception e)
		{
			// e.printStackTrace();
			return newErrorInstance();
		}
	}

}
