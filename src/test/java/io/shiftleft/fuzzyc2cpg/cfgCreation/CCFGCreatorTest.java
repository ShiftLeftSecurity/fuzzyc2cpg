package io.shiftleft.fuzzyc2cpg.cfgCreation;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.cfg.CCFGFactory;
import io.shiftleft.fuzzyc2cpg.cfg.CFG;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;
import io.shiftleft.fuzzyc2cpg.parseTreeToAST.FunctionContentTestUtil;

public class CCFGCreatorTest
{
	protected AstNode getASTForCode(String input)
	{
		return FunctionContentTestUtil.parseAndWalk(input);
	}

	protected CFG getCFGForCode(String input)
	{
		CCFGFactory ccfgFactory = new CCFGFactory();
		return CCFGFactory.convert(getASTForCode(input));
	}

	protected CfgNode getNodeByCode(CFG cfg, String code)
	{
		for (CfgNode node : cfg.getVertices())
		{
			if (node.toString().equals("[" + code + "]"))
			{
				return node;
			}
		}
		return null;
	}

	protected boolean contains(CFG cfg, String code)
	{
		return getNodeByCode(cfg, code) != null;
	}

	protected boolean isConnected(CFG cfg, String srcCode, String dstCode)
	{
		return cfg.isConnected(getNodeByCode(cfg, srcCode),
				getNodeByCode(cfg, dstCode));
	}

}
