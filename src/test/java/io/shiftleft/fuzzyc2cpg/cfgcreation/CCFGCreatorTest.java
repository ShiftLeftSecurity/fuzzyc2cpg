package io.shiftleft.fuzzyc2cpg.cfgcreation;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.cfg.CAstToCfgConverter;
import io.shiftleft.fuzzyc2cpg.cfg.CFG;
import io.shiftleft.fuzzyc2cpg.cfg.IAstToCfgConverter;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;
import io.shiftleft.fuzzyc2cpg.parsetreetoast.FunctionContentTestUtil;

public class CCFGCreatorTest
{
	protected AstNode getASTForCode(String input)
	{
		return FunctionContentTestUtil.parseAndWalk(input);
	}

	protected CFG getCFGForCode(String input)
	{
		IAstToCfgConverter ccfgFactory = new CAstToCfgConverter();
		return ccfgFactory.convert(getASTForCode(input));
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
