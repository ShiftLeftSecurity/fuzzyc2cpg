package io.shiftleft.fuzzyc2cpg.antlrparsers.moduleparser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.shiftleft.fuzzyc2cpg.ModuleParser;

public class TemplateTests extends ModuleParserTest {

  @Test
  public void testClassTemplate() {
    String input = "template <typename T> class Foo {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);
    assertEquals("(class_def (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name T))) >) (class_key class) (class_name (identifier Foo)) { })",
                 output);
  }

  @Test
  public void testMultipleClassTemplate() {
    String input = "template <typename K, typename V> class Foo {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);
    assertEquals("(class_def (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name K))) , (template_decl_param (template_decl_keyword typename) (template_name V))) >) (class_key class) (class_name (identifier Foo)) { })",
                 output);
  }

  @Test
  public void testClassTemplateTemplate() {
    String input = "template <template<typename, typename> typename M, typename K, typename V> class Foo {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);
    assertEquals("(class_def (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param_list (template_template template < (template_decl_keyword typename) , (template_decl_keyword typename) >) (template_decl_keyword typename) (template_name M)) , (template_decl_param (template_decl_keyword typename) (template_name K))) , (template_decl_param (template_decl_keyword typename) (template_name V))) >) (class_key class) (class_name (identifier Foo)) { })",
                 output);
  }

  @Test
  public void testSpecializedClassTemplate() {
    String input = "template <> class Foo<int> {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);
    assertEquals("(class_def (template_decl template < >) (class_key class) (class_name (identifier Foo)) (template_args < (template_args (base_type int)) >) { })",
                 output);
  }

  @Test
  public void testVariadicClassTemplate() {
    String input = "template <typename Args...> class Foo {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);
    assertEquals("(class_def (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name Args ...))) >) (class_key class) (class_name (identifier Foo)) { })",
                 output);
  }

  @Test
  public void testClassTemplateOverload() {
    String input = "template <typename A, bool B> class Foo {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);
    assertEquals("(class_def (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name A))) , (template_decl_param (identifier bool) (template_name B))) >) (class_key class) (class_name (identifier Foo)) { })",
                 output);
  }

  @Test
  public void testClassConstTemplate() {
    String input = "class Foo : public Bar<const Baz> {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);

    assertEquals("(class_def (class_key class) (class_name (identifier Foo)) (base_classes : (base_class (access_specifier public) (identifier Bar) (template_args < (template_args const (base_type Baz)) >))) { })",
                 output);
  }

  @Test
  public void testClassDoubleTemplate() {
    String input = "template <typename A> template <typename B> class Foo {};";
    ModuleParser parser = createParser(input);
    String output = parser.class_def().toStringTree(parser);

    assertEquals("(class_def (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name A))) >) (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name B))) >) (class_key class) (class_name (identifier Foo)) { })",
            output);
  }


  @Test
  public void testFunctionTemplate() {
    String input = "template <typename T> T foo(T t) {}";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);
    assertEquals("(function_def (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name T))) >) (return_type (type_name (base_type T))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type T))) (parameter_id (parameter_name (identifier t))))) )) (compound_statement { }))",
                 output);
  }

  @Test
  public void testMultipleFunctionTemplate() {
    String input = "template <typename K, typename V> V foo(K k) {}";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);
    assertEquals("(function_def (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name K))) , (template_decl_param (template_decl_keyword typename) (template_name V))) >) (return_type (type_name (base_type V))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type K))) (parameter_id (parameter_name (identifier k))))) )) (compound_statement { }))",
                 output);
  }

  @Test
  public void testFunctionTemplateTemplate() {
    String input = "template <template <typename, typename> typename M, typename K, typename V> M<K, V> foo(M<K, V> k) {}";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);
    assertEquals("(function_def (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param_list (template_template template < (template_decl_keyword typename) , (template_decl_keyword typename) >) (template_decl_keyword typename) (template_name M)) , (template_decl_param (template_decl_keyword typename) (template_name K))) , (template_decl_param (template_decl_keyword typename) (template_name V))) >) (return_type (type_name (base_type M) < (template_args (base_type K) , (base_type V)) >)) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type M) < (template_args (base_type K) , (base_type V)) >)) (parameter_id (parameter_name (identifier k))))) )) (compound_statement { }))",
                 output);
  }

  @Test
  public void testSpecializedFunctionTemplate() {
    String input = "template <> int foo(int y) {}";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);
    assertEquals("(function_def (template_decl template < >) (return_type (type_name (base_type int))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type int))) (parameter_id (parameter_name (identifier y))))) )) (compound_statement { }))",
                 output);
  }

  @Test
  public void testVariadicFunctionTemplate() {
    String input = "template <typename Args...> int Foo(Args... args) {};";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);
    assertEquals("(function_def (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name Args ...))) >) (return_type (type_name (base_type int))) (function_name (identifier Foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type Args ...))) (parameter_id (parameter_name (identifier args))))) )) (compound_statement { }))",
                 output);
  }

  @Test
  public void testFunctionTemplateOverload() {
    String input = "template <typename A, bool B> A Foo(B b) {};";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);
    assertEquals("(function_def (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name A))) , (template_decl_param (identifier bool) (template_name B))) >) (return_type (type_name (base_type A))) (function_name (identifier Foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type B))) (parameter_id (parameter_name (identifier b))))) )) (compound_statement { }))",
            output);
  }

  @Test
  public void testFunctionConstTemplate() {
    String input = "void foo(Foo<const Bar> foo) {}";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);

    assertEquals("(function_def (return_type (type_name (base_type void))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type Foo) < (template_args const (base_type Bar)) >)) (parameter_id (parameter_name (identifier foo))))) )) (compound_statement { }))",
                 output);
  }

  @Test
  public void testFunctionDoubleTemplate() {
    String input = "template <typename A> template <typename B> A do_a(B b) {}";
    ModuleParser parser = createParser(input);
    String output = parser.function_def().toStringTree(parser);

    assertEquals("(function_def (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name A))) >) (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name B))) >) (return_type (type_name (base_type A))) (function_name (identifier do_a)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type B))) (parameter_id (parameter_name (identifier b))))) )) (compound_statement { }))",
            output);
  }

  @Test
  public void testFunctionDeclTemplate() {
    String input = "template <typename T> T foo(T t);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);
    assertEquals("(function_decl (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name T))) >) (return_type (type_name (base_type T))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type T))) (parameter_id (parameter_name (identifier t))))) )) ;)",
                 output);
  }

  @Test
  public void testMultipleFunctionDeclTemplate() {
    String input = "template <typename K, typename V> V foo(K k);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);
    assertEquals("(function_decl (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name K))) , (template_decl_param (template_decl_keyword typename) (template_name V))) >) (return_type (type_name (base_type V))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type K))) (parameter_id (parameter_name (identifier k))))) )) ;)",
                 output);
  }

  @Test
  public void testFunctionDeclTemplateTemplate() {
    String input = "template <template <typename, typename> typename M, typename K, typename V> M<K, V> foo(M<K, V> k);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);
    assertEquals("(function_decl (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param_list (template_template template < (template_decl_keyword typename) , (template_decl_keyword typename) >) (template_decl_keyword typename) (template_name M)) , (template_decl_param (template_decl_keyword typename) (template_name K))) , (template_decl_param (template_decl_keyword typename) (template_name V))) >) (return_type (type_name (base_type M) < (template_args (base_type K) , (base_type V)) >)) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type M) < (template_args (base_type K) , (base_type V)) >)) (parameter_id (parameter_name (identifier k))))) )) ;)",
                 output);
  }

  @Test
  public void testSpecializedFunctionDeclTemplate() {
    String input = "template <> int foo(int y);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);
    assertEquals("(function_decl (template_decl template < >) (return_type (type_name (base_type int))) (function_name (identifier foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type int))) (parameter_id (parameter_name (identifier y))))) )) ;)",
                 output);
  }

  @Test
  public void testVariadicFunctionDeclTemplate() {
    String input = "template <typename Args...> int Foo(Args... args);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);
    assertEquals("(function_decl (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name Args ...))) >) (return_type (type_name (base_type int))) (function_name (identifier Foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type Args ...))) (parameter_id (parameter_name (identifier args))))) )) ;)",
                 output);
  }

  @Test
  public void testFunctionDeclTemplateOverload() {
    String input = "template <typename A, bool B> A Foo(B b);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);
    assertEquals("(function_decl (template_decl template < (template_decl_param_list (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name A))) , (template_decl_param (identifier bool) (template_name B))) >) (return_type (type_name (base_type A))) (function_name (identifier Foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type B))) (parameter_id (parameter_name (identifier b))))) )) ;)",
            output);
  }

  @Test
  public void testFunctionDeclConstTemplate() {
    String input = "void process_foo(Foo<const Bar> foo);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);

    assertEquals("(function_decl (return_type (type_name (base_type void))) (function_name (identifier process_foo)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type Foo) < (template_args const (base_type Bar)) >)) (parameter_id (parameter_name (identifier foo))))) )) ;)",
                 output);
  }

  @Test
  public void testFunctionDeclDoubleTemplate() {
    String input = "template <typename A> template <typename B> A do_a(B b);";
    ModuleParser parser = createParser(input);
    String output = parser.function_decl().toStringTree(parser);

    assertEquals("(function_decl (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name A))) >) (template_decl template < (template_decl_param_list (template_decl_param (template_decl_keyword typename) (template_name B))) >) (return_type (type_name (base_type A))) (function_name (identifier do_a)) (function_param_list ( (parameter_decl_clause (parameter_decl (param_decl_specifiers (type_name (base_type B))) (parameter_id (parameter_name (identifier b))))) )) ;)",
                 output);
  }
}
