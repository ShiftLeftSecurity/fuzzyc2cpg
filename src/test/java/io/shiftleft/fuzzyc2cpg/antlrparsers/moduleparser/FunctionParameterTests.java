package io.shiftleft.fuzzyc2cpg.antlrparsers.moduleparser;

import io.shiftleft.fuzzyc2cpg.ModuleParser;
import org.junit.Test;

import static org.junit.Assert.assertTrue;


public class FunctionParameterTests extends FunctionDefinitionTests
{

	@Test
	public void testFunctionPtrParam()
	{
		String input = "int foo(char *(*param)(void)){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);

		assertTrue(output.startsWith(
				"(function_def (return_type (type_name (base_type int))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type char))) (parameter_id (ptrs (ptr_operator *)) ( (parameter_id (ptrs (ptr_operator *)) (parameter_name (identifier param))) ) (type_suffix (param_type_list ( void )))))) )) (compound_statement { }))"));
	}

	@Test
	public void testVoidParamList()
	{
		String input = "static int altgid(void){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);
		assertTrue(output.startsWith("(function_def "));
	}

	@Test
	public void testParamVoidPtr()
	{
		String input = "static int altgid(void *ptr){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);
		assertTrue(output.startsWith("(function_def"));
	}

	@Test
	public void testLinux__user()
	{
		String input = "static long aio_read_events_ring(struct kioctx *ctx, struct io_event __user *event, long nr){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);
		assertTrue(output.startsWith("(function_def"));
	}

	@Test
	public void testParamConstVoidPtr()
	{
		String input = "static ssize_t _7z_write_data(struct archive_write *a, const void *buff, size_t s){}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);
		assertTrue(output.startsWith("(function_def"));
	}

	@Test
	public void testConstConstPtr()
	{
		String input = "static void black_box(const example_s * const * alias_to_alias) {}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);
		assertTrue(output.startsWith("(function_def"));
	}
	@Test
	public void testVoidConstArgs() {
		String input = "#include <string.h>\n" +
				"#include <pthread.h>\n" +
				"#include <stdlib.h>\n" +
				"#include <unistd.h>\n" +
				"#include <stdint.h>\n" +
				"\n" +
				"/* Basic types disallowed. */\n" +
				"typedef signed int SINT_32;\n" +
				"/* Basic types disallowed. */\n" +
				"typedef unsigned int UINT_32;\n" +
				"\n" +
				"/* Prototypes always required. */\n" +
				"SINT_32 pthread_create(\n" +
				"    pthread_t * thread,\n" +
				"    const pthread_attr_t * attr,\n" +
				"    void * (*start_routine) (void *),\n" +
				"    void * arg);\n" +
				"/* Prototypes always required. */\n" +
				"SINT_32 pthread_join(\n" +
				"    pthread_t thread,\n" +
				"    void ** value_ptr);\n" +
				"/* Prototypes always required. */\n" +
				"UINT_32 sleep(UINT_32 seconds);\n" +
				"\n" +
				"/* Example data, arbitrary. */\n" +
				"typedef struct { int32_t tux; int32_t baz; int32_t bar; } example_s;\n" +
				"\n" +
				"/* Corrupts input. */\n" +
				"static void * black_box(void * const args)\n" +
				"{\n" +
				"    /* Reference to resource shared across thread_count. */\n" +
				"    example_s * alias = (example_s*)args;\n" +
				"\n" +
				"    /* Arbitrary bound. */\n" +
				"    while ((alias != NULL) && (alias->tux < 10))\n" +
				"    {\n" +
				"        /* \"Think\" to simulate some ammount of work. */\n" +
				"        (void)sleep(0);\n" +
				"\n" +
				"        /* If this occurs another thread has changed the shared resource. */\n" +
				"        if (alias->tux >= 10)\n" +
				"        {\n" +
				"            alias += 2048;\n" +
				"        }\n" +
				"        else\n" +
				"        {\n" +
				"            /* Increment potentially corrupted alias. */\n" +
				"            alias->tux += 1;\n" +
				"        }\n" +
				"    }\n" +
				"\n" +
				"    /* Can't return NULL. */\n" +
				"    return (int32_t*)0;\n" +
				"}";

		ModuleParser parser = createParser(input);
		String output = parser.function_def().toStringTree(parser);

		assertTrue(output.startsWith("(function_def"));
	}
}
