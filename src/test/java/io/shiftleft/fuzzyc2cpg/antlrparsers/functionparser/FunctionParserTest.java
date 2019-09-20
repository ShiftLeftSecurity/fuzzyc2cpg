package io.shiftleft.fuzzyc2cpg.antlrparsers.functionparser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.shiftleft.fuzzyc2cpg.parser.AntlrParserDriver;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Test;

public class FunctionParserTest extends FunctionParserTestBase
{

	@Test
	public void testIf()
	{
		String input = "if(foo){}";
		AntlrParserDriver functionParser = createFunctionDriver();
		ParseTree tree = functionParser.parseString(input);
		String output = tree.toStringTree(functionParser.getAntlrParser());
		assertTrue(output.contains("(selection_or_iteration if"));
	}

	@Test
	public void testStructInFunc()
	{
		String input = "class foo{ int x; };";
		AntlrParserDriver functionParser = createFunctionDriver();
		ParseTree tree = functionParser.parseString(input);
		String output = tree.toStringTree(functionParser.getAntlrParser());
		assertTrue(output.contains("class_def"));
	}

	@Test
	public void testSizeofStruct()
	{
		String input = "while((buffer + len) > (tmp + sizeof(struct stun_attrib))) {}";
		AntlrParserDriver functionParser = createFunctionDriver();
		ParseTree tree = functionParser.parseString(input);
		String output = tree.toStringTree(functionParser.getAntlrParser());
		assertTrue(output.contains("selection_or_iteration while"));
	}

	@Test
	public void testArrayInitializerList() {
		String input = "int x[] = { 1, 2, 3, 4, 5 };";
		AntlrParserDriver functionParser = createFunctionDriver();
		ParseTree tree = functionParser.parseString(input);
		String output = tree.toStringTree(functionParser.getAntlrParser());
		assertTrue(output.contains("(initializer_list"));
	}

	// TODO: Need to add both of the below tests to the ModuleParser tests, too.
	@Test
	public void testArrayDesignatedInitialiser() {
		String input = "int x[5] = { [0] = 1, [4] = 2 };";
		AntlrParserDriver functionParser = createFunctionDriver();
		ParseTree tree = functionParser.parseString(input);
		String output = tree.toStringTree(functionParser.getAntlrParser());
		assertFalse(output.contains("(water x"));
	}

	@Test
	public void testMemberDesignatedInitialiser() {
		String input = "struct foo = { .n = 1, .s = \"hello\" };";
		AntlrParserDriver functionParser = createFunctionDriver();
		ParseTree tree = functionParser.parseString(input);
		String output = tree.toStringTree(functionParser.getAntlrParser());
		assertFalse(output.contains("(water x"));
	}
}
