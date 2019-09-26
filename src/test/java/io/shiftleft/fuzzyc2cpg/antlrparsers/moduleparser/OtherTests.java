package io.shiftleft.fuzzyc2cpg.antlrparsers.moduleparser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.shiftleft.fuzzyc2cpg.ModuleParser;
import org.junit.Test;

public class OtherTests extends ModuleParserTest
{

	@Test
	public void testNestedFunctionName()
	{
		String input = "int (foo)(){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);
		assertTrue(output.startsWith("(function_def "));
	}

	@Test
	public void testOperatorOverloading()
	{
		String input = "inline bool operator == (const PlMessageHeader &b) const {}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);

		assertTrue(output.startsWith("(function_def "));
	}

	@Test
	public void testExceptionSpecificationCpp()
	{
		String input = "int foo() throw(){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);

		assertTrue(output.startsWith("(function_def "));
	}

	@Test
	public void testMultilineString() {
		String input = "char* c = \"This is \"\n" +
			"\"a multiline \"\n" +
			"\"string.\";";

		ModuleParser parser = createParser(input);
		String output = parser.var_decl().toStringTree(parser);
		assertEquals(
			"(var_decl (type_name (base_type char)) (init_declarator_list (init_declarator (declarator (ptrs (ptr_operator *)) (identifier c)) = (assign_expr_w_ (assign_water \"This is \"\\n\"a multiline \"\\n\"string.\"))) ;))",
			output);
	}

	@Test
	public void testStringConcatWithIdentifier() {
		String input = "char* c = \"start\"SOME_VAR\"end\";";
		ModuleParser parser = createParser(input);
		String output = parser.var_decl().toStringTree(parser);
		assertEquals(
			"(var_decl (type_name (base_type char)) (init_declarator_list (init_declarator (declarator (ptrs (ptr_operator *)) (identifier c)) = (assign_expr_w_ (assign_water \"start\"SOME_VAR\"end\"))) ;))",
			output);
	}
}
