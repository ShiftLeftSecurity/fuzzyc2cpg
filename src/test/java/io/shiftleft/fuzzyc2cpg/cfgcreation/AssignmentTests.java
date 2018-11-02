package io.shiftleft.fuzzyc2cpg.cfgcreation;

import static org.junit.Assert.assertTrue;

import io.shiftleft.fuzzyc2cpg.cfg.CFG;
import org.junit.Test;


public class AssignmentTests extends CCFGCreatorTest
{
	@Test
	public void testSingleAssignmentBlockNumber()
	{
		String input = "x = y;";
		CFG cfg = getCFGForCode(input);
		assertTrue(cfg.size() == 5);
	}

	@Test
	public void testAssignmentASTLink()
	{
		String input = "x = 10;";
		CFG cfg = getCFGForCode(input);
		assertTrue(getNodeByCode(cfg, "x = 10") != null);
	}

	@Test
	public void testAssignmentInDecl()
	{
		String input = "int x = 10;";
		CFG cfg = getCFGForCode(input);
		assertTrue(cfg.size() == 5);
	}

}
