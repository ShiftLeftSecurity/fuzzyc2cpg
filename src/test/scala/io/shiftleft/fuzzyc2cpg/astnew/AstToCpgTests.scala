package io.shiftleft.fuzzyc2cpg.astnew

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes, Operators}
import io.shiftleft.fuzzyc2cpg.{ModuleLexer, Utils}
import io.shiftleft.fuzzyc2cpg.ast.{AstNode, AstNodeBuilder}
import io.shiftleft.fuzzyc2cpg.astnew.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.astnew.NodeProperty.NodeProperty
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.fuzzyc2cpg.parser.{AntlrParserDriverObserver, TokenSubStream}
import org.antlr.v4.runtime.{CharStreams, ParserRuleContext}
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.scalatest.{Matchers, WordSpec}

class AstToCpgTests extends WordSpec with Matchers {

  private class GraphAdapter(graph: ScalaGraph) extends CpgAdapter[Vertex, Vertex] {
    override def createNodeBuilder(kind: NodeKind): Vertex = {
      graph.addVertex(kind.toString)
    }

    override def createNode(vertex: Vertex): Vertex = {
      vertex
    }

    override def addProperty(vertex: Vertex, property: NodeProperty, value: String): Unit = {
      vertex.property(property.toString, value)
    }

    override def addProperty(vertex: Vertex, property: NodeProperty, value: Int): Unit = {
      vertex.property(property.toString, value)
    }

    override def addProperty(vertex: Vertex, property: NodeProperty, value: Boolean): Unit = {
      vertex.property(property.toString, value)
    }

    override def addEdge(edgeKind: EdgeKind, dstNode: Vertex, srcNode: Vertex): Unit = {
      srcNode.addEdge(edgeKind.toString, dstNode)
    }
  }

  private implicit class VertexListWrapper(vertexList: List[Vertex]) {
    def expandAst(filterLabels: String*): List[Vertex] = {
      if (filterLabels.nonEmpty) {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).hasLabel(filterLabels.head, filterLabels.tail: _*).l)
      } else {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).l)
      }
    }

    def filterOrder(order: Int): List[Vertex] = {
      vertexList.filter(_.value2(NodeKeys.ORDER) == order)
    }

    def checkForSingle[T](propertyName: Key[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.value2(propertyName) shouldBe value
    }

    def checkForSingle(): Unit = {
      vertexList.size shouldBe 1
    }

    def check[A](count: Int, mapFunc: Vertex => A, expectations: A*): Unit = {
      vertexList.size shouldBe count
      vertexList.map(mapFunc).toSet shouldBe expectations.toSet
    }

  }

  class Fixture(code: String) {

    private class DriverObserver extends AntlrParserDriverObserver {
      override def begin(): Unit = {}

      override def end(): Unit = {}

      override def startOfUnit(ctx: ParserRuleContext, filename: String): Unit = {}

      override def endOfUnit(ctx: ParserRuleContext, filename: String): Unit = {}

      override def processItem(node: AstNode, builderStack: java.util.Stack[AstNodeBuilder]): Unit = {
        nodes = node :: nodes
      }
    }

    private var nodes = List[AstNode]()

    private val driver = new AntlrCModuleParserDriver()
    driver.addObserver(new DriverObserver())

    private val inputStream = CharStreams.fromString(code)
    private val lex = new ModuleLexer(inputStream)
    private val tokens = new TokenSubStream(lex)

    driver.parseAndWalkTokenStream(tokens)

    private val fileName = "codeFromString"
    val graph = TinkerGraph.open().asScala()
    private val astParentNode = graph.addVertex("PARENT")
    protected val astParent = List(astParentNode)
    private val cpgAdapter = new GraphAdapter(graph)

    nodes.foreach { node =>
      val astToProtoConverter = new AstToCpgConverter(fileName, astParentNode, cpgAdapter)
      astToProtoConverter.convert(node)
    }

    def getMethod(name: String): List[Vertex] = {
      val result = graph.V
        .hasLabel(NodeTypes.METHOD)
        .has(NodeKeys.NAME -> name)
        .l

      result.size shouldBe 1
      result
    }

    def getTypeDecl(name: String): List[Vertex] = {
      val result = graph.V
        .hasLabel(NodeTypes.TYPE_DECL)
        .has(NodeKeys.NAME -> name)
        .l

      result.size shouldBe 1
      result
    }
  }

  "Method AST layout" should {
    "be correct for empty method" in new Fixture("""
        |void method(int x) {
        |}"
      """.stripMargin) {
      val method = getMethod("method")
      method.expandAst(NodeTypes.BLOCK).checkForSingle()

      method
        .expandAst(NodeTypes.METHOD_RETURN)
        .checkForSingle(NodeKeys.TYPE_FULL_NAME, "void")

      method
        .expandAst(NodeTypes.METHOD_PARAMETER_IN)
        .checkForSingle(NodeKeys.TYPE_FULL_NAME, "int")
    }

    "be correct for decl assignment" in new Fixture("""
        |void method() {
        |  int local = 1;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val local = block.expandAst(NodeTypes.LOCAL)
      local.checkForSingle(NodeKeys.NAME, "local")
      local.checkForSingle(NodeKeys.TYPE_FULL_NAME, "int")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val arguments = assignment.expandAst()
      arguments.check(
        2,
        arg =>
          (arg.label,
           arg.value2(NodeKeys.CODE),
           arg.value2(NodeKeys.TYPE_FULL_NAME),
           arg.value2(NodeKeys.ORDER),
           arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "local", "int", 1, 1),
        (NodeTypes.LITERAL, "1", "int", 2, 2)
      )
    }

    "be correct for decl assignment with identifier on right hand side" in new Fixture("""
        |void method(int x) {
        |  int local = x;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val local = block.expandAst(NodeTypes.LOCAL)
      local.checkForSingle(NodeKeys.NAME, "local")
      local.checkForSingle(NodeKeys.TYPE_FULL_NAME, "int")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val arguments = assignment.expandAst()
      arguments.check(
        2,
        arg =>
          (arg.label,
           arg.value2(NodeKeys.CODE),
           arg.value2(NodeKeys.TYPE_FULL_NAME),
           arg.value2(NodeKeys.ORDER),
           arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "local", "int", 1, 1),
        (NodeTypes.IDENTIFIER, "x", "int", 2, 2)
      )
    }

    "be correct for decl assignment of multiple locals" in new Fixture("""
        |void method(int x, int y) {
        |  int local = x, local2 = y;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      block
        .expandAst(NodeTypes.LOCAL)
        .check(
          2,
          local => (local.label, local.value2(NodeKeys.CODE), local.value2(NodeKeys.TYPE_FULL_NAME)),
          expectations = (NodeTypes.LOCAL, "local", "int"),
          (NodeTypes.LOCAL, "local2", "int")
        )

      val assignment1 = block.expandAst(NodeTypes.CALL).filterOrder(1)
      assignment1.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val arguments1 = assignment1.expandAst()
      arguments1.check(
        2,
        arg =>
          (arg.label,
           arg.value2(NodeKeys.CODE),
           arg.value2(NodeKeys.TYPE_FULL_NAME),
           arg.value2(NodeKeys.ORDER),
           arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "local", "int", 1, 1),
        (NodeTypes.IDENTIFIER, "x", "int", 2, 2)
      )

      val assignment2 = block.expandAst(NodeTypes.CALL).filterOrder(2)
      assignment2.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val arguments2 = assignment2.expandAst()
      arguments2.check(
        2,
        arg =>
          (arg.label,
           arg.value2(NodeKeys.CODE),
           arg.value2(NodeKeys.TYPE_FULL_NAME),
           arg.value2(NodeKeys.ORDER),
           arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "local2", "int", 1, 1),
        (NodeTypes.IDENTIFIER, "y", "int", 2, 2)
      )
    }

    "be correct for nested expression" in new Fixture("""
        |void method() {
        |  int x;
        |  int y;
        |  int z;
        |
        |  x = y + z;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val locals = block.expandAst(NodeTypes.LOCAL)
      locals.check(3, local => local.value2(NodeKeys.NAME), expectations = "x", "y", "z")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val rightHandSide = assignment.expandAst(NodeTypes.CALL).filterOrder(2)
      rightHandSide.checkForSingle(NodeKeys.NAME, Operators.addition)

      val arguments = rightHandSide.expandAst()
      arguments.check(
        2,
        arg => (arg.label, arg.value2(NodeKeys.CODE), arg.value2(NodeKeys.ORDER), arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "y", 1, 1),
        (NodeTypes.IDENTIFIER, "z", 2, 2)
      )
    }

    "be correct for nested block" in new Fixture("""
        |void method() {
        |  int x;
        |  {
        |    int y;
        |  }
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val locals = block.expandAst(NodeTypes.LOCAL)
      locals.checkForSingle(NodeKeys.NAME, "x")

      val nestedBlock = block.expandAst(NodeTypes.BLOCK)
      nestedBlock.checkForSingle()
      val nestedLocals = nestedBlock.expandAst(NodeTypes.LOCAL)
      nestedLocals.checkForSingle(NodeKeys.NAME, "y")
    }

    "be correct for while-loop" in new Fixture("""
        |void method(int x) {
        |  while (x < 1) {
        |    x += 1;
        |  }
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val whileStmt = block.expandAst(NodeTypes.CONTROL_STRUCTURE)
      whileStmt.check(1, _.value2(NodeKeys.CODE), expectations = "while (x < 1)")
      whileStmt.check(1, whileStmt => whileStmt.value2(NodeKeys.PARSER_TYPE_NAME), expectations = "WhileStatement")

      val lessThan = whileStmt.expandAst(NodeTypes.CALL)
      lessThan.checkForSingle(NodeKeys.NAME, Operators.lessThan)

      val whileBlock = whileStmt.expandAst(NodeTypes.BLOCK)
      whileBlock.checkForSingle()

      val assignPlus = whileBlock.expandAst(NodeTypes.CALL)
      assignPlus.filterOrder(1).checkForSingle(NodeKeys.NAME, Operators.assignmentPlus)
    }

    "be correct for if" in new Fixture("""
        |void method(int x) {
        |  int y;
        |  if (x > 0) {
        |    y = 0;
        |  }
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val ifStmt = block.expandAst(NodeTypes.CONTROL_STRUCTURE)
      ifStmt.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME), expectations = "IfStatement")

      val greaterThan = ifStmt.expandAst(NodeTypes.CALL)
      greaterThan.checkForSingle(NodeKeys.NAME, Operators.greaterThan)

      val ifBlock = ifStmt.expandAst(NodeTypes.BLOCK)
      ifBlock.checkForSingle()

      val assignment = ifBlock.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)
    }

    "be correct for if-else" in new Fixture("""
        |void method(int x) {
        |  int y;
        |  if (x > 0) {
        |    y = 0;
        |  } else {
        |    y = 1;
        |  }
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val ifStmt = block.expandAst(NodeTypes.CONTROL_STRUCTURE)
      ifStmt.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME), expectations = "IfStatement")

      val greaterThan = ifStmt.expandAst(NodeTypes.CALL)
      greaterThan.checkForSingle(NodeKeys.NAME, Operators.greaterThan)

      val ifBlock = ifStmt.expandAst(NodeTypes.BLOCK)
      ifBlock.checkForSingle()

      val assignment = ifBlock.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val elseStmt = ifStmt.expandAst(NodeTypes.CONTROL_STRUCTURE)
      elseStmt.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME), expectations = "ElseStatement")
      elseStmt.check(1, _.value2(NodeKeys.CODE), "else")

      val elseBlock = elseStmt.expandAst(NodeTypes.BLOCK)
      elseBlock.checkForSingle()

      val assignmentInElse = elseBlock.expandAst(NodeTypes.CALL)
      assignmentInElse.checkForSingle(NodeKeys.NAME, Operators.assignment)
    }

    "be correct for conditional expression" in new Fixture(
      """
        | void method() {
        |   int x = (foo == 1) ? bar : 0;
        | }
      """.stripMargin
    ) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val call = block.expandAst(NodeTypes.CALL)
      val condition = call.expandAst(NodeTypes.CONTROL_STRUCTURE)
      condition.check(1, _.value2(NodeKeys.CODE), expectations = "(foo == 1) ? bar : 0")
      condition.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME), expectations = "ConditionalExpression")
    }

    "be correct for for-loop with multiple initializations" in new Fixture("""
        |void method(int x, int y) {
        |  for ( x = 0, y = 0; x < 1; x += 1) {
        |    int z = 0;
        |  }
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val forLoop = block.expandAst(NodeTypes.CONTROL_STRUCTURE)
      forLoop.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME), expectations = "ForStatement")
      forLoop.check(1, _.value2(NodeKeys.CODE), expectations = "for ( x = 0, y = 0; x < 1; x += 1)")

      val initBlock = forLoop.expandAst(NodeTypes.BLOCK).filterOrder(1)
      initBlock.checkForSingle()

      val assignments = initBlock.expandAst(NodeTypes.CALL)
      assignments.check(2, _.value2(NodeKeys.NAME), expectations = Operators.assignment)

      val condition = forLoop.expandAst(NodeTypes.CALL).filterOrder(2)
      condition.checkForSingle(NodeKeys.NAME, Operators.lessThan)

      val increment = forLoop.expandAst(NodeTypes.CALL).filterOrder(3)
      increment.checkForSingle(NodeKeys.NAME, Operators.assignmentPlus)

      val forBlock = forLoop.expandAst(NodeTypes.BLOCK).filterOrder(4)
      forBlock.checkForSingle()
    }

    "be correct for unary expression '+'" in new Fixture("""
        |void method(int x) {
        |  +x;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val plusCall = block.expandAst(NodeTypes.CALL)
      plusCall.checkForSingle(NodeKeys.NAME, Operators.plus)

      val identifierX = plusCall.expandAst(NodeTypes.IDENTIFIER)
      identifierX.checkForSingle(NodeKeys.NAME, "x")
    }

    "be correct for unary expression '++'" in new Fixture("""
        |void method(int x) {
        |  ++x;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val plusCall = block.expandAst(NodeTypes.CALL)
      plusCall.checkForSingle(NodeKeys.NAME, Operators.preIncrement)

      val identifierX = plusCall.expandAst(NodeTypes.IDENTIFIER)
      identifierX.checkForSingle(NodeKeys.NAME, "x")
    }

    "be correct for call expression" in new Fixture("""
        |void method(int x) {
        |  foo(x);
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val call = block.expandAst(NodeTypes.CALL)
      call.checkForSingle(NodeKeys.NAME, "foo")

      val argumentX = call.expandAst(NodeTypes.IDENTIFIER)
      argumentX.checkForSingle(NodeKeys.NAME, "x")
    }

    "be correct for pointer call expression" in new Fixture("""
        |void method(int x) {
        |  (*funcPointer)(x);
        |}
      """.stripMargin) {}

    "be correct for member access" in new Fixture("""
        |void method(struct someUndefinedStruct x) {
        |  x.a;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val memberAccess = block.expandAst(NodeTypes.CALL)
      memberAccess.checkForSingle(NodeKeys.NAME, Operators.memberAccess)

      val arguments = memberAccess.expandAst(NodeTypes.IDENTIFIER)
      arguments.check(2, arg => {
        (arg.value2(NodeKeys.NAME), arg.value2(NodeKeys.ARGUMENT_INDEX))
      }, expectations = ("x", 1), ("a", 2))
    }

    "be correct for indirect member access" in new Fixture("""
        |void method(struct someUndefinedStruct *x) {
        |  x->a;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val memberAccess = block.expandAst(NodeTypes.CALL)
      memberAccess.checkForSingle(NodeKeys.NAME, Operators.indirectMemberAccess)

      val arguments = memberAccess.expandAst(NodeTypes.IDENTIFIER)
      arguments.check(2, arg => {
        (arg.value2(NodeKeys.NAME), arg.value2(NodeKeys.ARGUMENT_INDEX))
      }, expectations = ("x", 1), ("a", 2))
    }

    "be correct for sizeof operator on identifier with brackets" in new Fixture(
      """
        |void method() {
        |  int a;
        |  sizeof(a);
        |}
      """.stripMargin
    ) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val sizeof = block.expandAst(NodeTypes.CALL)
      sizeof.checkForSingle(NodeKeys.NAME, Operators.sizeOf)

      val arguments = sizeof.expandAst(NodeTypes.IDENTIFIER)
      arguments.checkForSingle(NodeKeys.NAME, "a")
      arguments.checkForSingle(NodeKeys.ARGUMENT_INDEX, new Integer(1))
    }

    "be correct for sizeof operator on identifier without brackets" in new Fixture(
      """
        |void method() {
        |  int a;
        |  sizeof a ;
        |}
      """.stripMargin
    ) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val sizeof = block.expandAst(NodeTypes.CALL)
      sizeof.checkForSingle(NodeKeys.NAME, Operators.sizeOf)

      val arguments = sizeof.expandAst(NodeTypes.IDENTIFIER)
      arguments.checkForSingle(NodeKeys.NAME, "a")
      arguments.checkForSingle(NodeKeys.ARGUMENT_INDEX, new Integer(1))
    }

    "be correct for sizeof operator on type" in new Fixture(
      """
        |void method() {
        |  sizeof(int);
        |}
      """.stripMargin
    ) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val sizeof = block.expandAst(NodeTypes.CALL)
      sizeof.checkForSingle(NodeKeys.NAME, Operators.sizeOf)

      // For us it is undecidable whether "int" is a type or an Identifier
      // Thus the implementation always goes for Identifier which we encode
      // here in the tests.
      val arguments = sizeof.expandAst(NodeTypes.IDENTIFIER)
      arguments.checkForSingle(NodeKeys.NAME, "int")
      arguments.checkForSingle(NodeKeys.ARGUMENT_INDEX, new Integer(1))
    }
  }

  "Structural AST layout" should {
    "be correct for empty method" in new Fixture("""
        | void method() {
        | };
      """.stripMargin) {
      val method = getMethod("method")
      method.checkForSingle()

      astParent.expandAst(NodeTypes.METHOD) shouldBe method
    }

    "be correct for empty named struct" in new Fixture("""
        | struct foo {
        | };
      """.stripMargin) {
      val typeDecl = getTypeDecl("foo")
      typeDecl.checkForSingle()

      astParent.expandAst(NodeTypes.TYPE_DECL) shouldBe typeDecl
    }

    "be correct for named struct with single field" in new Fixture("""
        | struct foo {
        |   int x;
        | };
      """.stripMargin) {
      val typeDecl = getTypeDecl("foo")
      typeDecl.checkForSingle()
      val member = typeDecl.expandAst(NodeTypes.MEMBER)
      member.checkForSingle(NodeKeys.CODE, "x")
      member.checkForSingle(NodeKeys.NAME, "x")
      member.checkForSingle(NodeKeys.TYPE_FULL_NAME, "int")
    }

    "be correct for named struct with multiple fields" in new Fixture("""
        | struct foo {
        |   int x;
        |   int y;
        |   int z;
        | };
      """.stripMargin) {
      val typeDecl = getTypeDecl("foo")
      typeDecl.checkForSingle()
      val member = typeDecl.expandAst(NodeTypes.MEMBER)
      member.check(3, member => member.value2(NodeKeys.CODE), expectations = "x", "y", "z")
    }

    "be correct for named struct with nested struct" in new Fixture("""
        | struct foo {
        |   int x;
        |   struct bar {
        |     int y;
        |     struct foo2 {
        |       int z;
        |     };
        |   };
        | };
      """.stripMargin) {
      val typeDeclFoo = getTypeDecl("foo")
      typeDeclFoo.checkForSingle()
      val memberFoo = typeDeclFoo.expandAst(NodeTypes.MEMBER)
      memberFoo.checkForSingle(NodeKeys.CODE, "x")

      val typeDeclBar = typeDeclFoo.expandAst(NodeTypes.TYPE_DECL)
      typeDeclBar.checkForSingle(NodeKeys.TYPE_DECL.FULL_NAME, "bar")
      val memberBar = typeDeclBar.expandAst(NodeTypes.MEMBER)
      memberBar.checkForSingle(NodeKeys.CODE, "y")

      val typeDeclFoo2 = typeDeclBar.expandAst(NodeTypes.TYPE_DECL)
      typeDeclFoo2.checkForSingle(NodeKeys.TYPE_DECL.FULL_NAME, "foo2")
      val memberFoo2 = typeDeclFoo2.expandAst(NodeTypes.MEMBER)
      memberFoo2.checkForSingle(NodeKeys.CODE, "z")
    }

    "be correct for typedef" in new Fixture(
      """
        |typedef struct foo {
        |} abc;
      """.stripMargin
    ) {
      val aliasTypeDecl = getTypeDecl("abc")

      aliasTypeDecl.checkForSingle(NodeKeys.FULL_NAME, "abc")
      aliasTypeDecl.checkForSingle(NodeKeys.ALIAS_TYPE_FULL_NAME, "foo")
    }

  }

  "AST" should {
    "have correct line number for method content" in new Fixture("""
        |
        |
        |
        |
        | void method(int x) {
        |
        |   x = 1;
        | }
      """.stripMargin) {
      val method = getMethod("method")
      method.checkForSingle[Integer](NodeKeys.LINE_NUMBER, 6)

      val block = method.expandAst(NodeTypes.BLOCK)

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)
      assignment.checkForSingle[Integer](NodeKeys.LINE_NUMBER, 8)
    }
  }
}
