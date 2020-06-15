package io.shiftleft.fuzzyc2cpg.astnew

import gremlin.scala._
import org.antlr.v4.runtime.{CharStreams, ParserRuleContext}
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.scalatest.{Matchers, WordSpec}

import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes, Operators}
import io.shiftleft.fuzzyc2cpg.ModuleLexer
import io.shiftleft.fuzzyc2cpg.adapter.CpgAdapter
import io.shiftleft.fuzzyc2cpg.adapter.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.adapter.EdgeProperty.EdgeProperty
import io.shiftleft.fuzzyc2cpg.adapter.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.adapter.NodeProperty.NodeProperty
import io.shiftleft.fuzzyc2cpg.ast.{AstNode, AstNodeBuilder}
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.fuzzyc2cpg.parser.{AntlrParserDriverObserver, TokenSubStream}

class AstToCpgTests extends WordSpec with Matchers {

  private class GraphAdapter(graph: ScalaGraph) extends CpgAdapter[Vertex, Vertex, Edge, Edge] {
    override def createNodeBuilder(kind: NodeKind): Vertex = {
      graph.addVertex(kind.toString)
    }

    override def createNode(vertex: Vertex, origAstNode: AstNode): Vertex = {
      vertex
    }

    override def createNode(vertex: Vertex): Vertex = {
      vertex
    }

    override def addNodeProperty(vertex: Vertex, property: NodeProperty, value: String): Unit = {
      vertex.property(property.toString, value)
    }

    override def addNodeProperty(vertex: Vertex, property: NodeProperty, value: Int): Unit = {
      vertex.property(property.toString, value)
    }

    override def addNodeProperty(vertex: Vertex, property: NodeProperty, value: Boolean): Unit = {
      vertex.property(property.toString, value)
    }

    override def addNodeProperty(vertex: Vertex, property: NodeProperty, value: List[String]): Unit = {
      vertex.property(property.toString, value)
    }

    override def createEdgeBuilder(dst: Vertex, src: Vertex, edgeKind: EdgeKind): Edge = {
      src.addEdge(edgeKind.toString, dst)
    }

    override def createEdge(edge: Edge): Edge = {
      edge
    }

    // Not used in test with this adapter.
    override def addEdgeProperty(edgeBuilder: Edge, property: EdgeProperty, value: String): Unit = ???
    override def mapNode(astNode: AstNode): Vertex = ???
  }

  private implicit class VertexListWrapper(vertexList: List[Vertex]) {
    def expandAst(filterLabels: String*): List[Vertex] = {
      if (filterLabels.nonEmpty) {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).hasLabel(filterLabels.head, filterLabels.tail: _*).l)
      } else {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).l)
      }
    }

    def expandCondition: List[Vertex] =
      vertexList.flatMap(_.start.out(EdgeTypes.CONDITION).l)

    def expandArgument: List[Vertex] =
      vertexList.flatMap(_.start.out(EdgeTypes.ARGUMENT).l)

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

      override def processItem[T <: AstNode](node: T,
                                             builderStack: java.util.Stack[AstNodeBuilder[_ <: AstNode]]): Unit = {
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

    val graph: ScalaGraph = TinkerGraph.open()
    private val astParentNode = graph.addVertex("NAMESPACE_BLOCK")
    protected val astParent = List(astParentNode)
    private val cpgAdapter = new GraphAdapter(graph)

    nodes.foreach { node =>
      val astToProtoConverter = new AstToCpgConverter(astParentNode, cpgAdapter)
      astToProtoConverter.convert(node)
    }

    def getMethod(name: String): List[Vertex] =
      getVertices(name, NodeTypes.METHOD)

    def getTypeDecl(name: String): List[Vertex] =
      getVertices(name, NodeTypes.TYPE_DECL)

    def getCall(name: String): List[Vertex] =
      getVertices(name, NodeTypes.CALL)

    def getVertices(name: String, nodeType: String): List[Vertex] = {
      val result = graph.V
        .hasLabel(nodeType)
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

      val condition = whileStmt.expandCondition
      condition.checkForSingle(NodeKeys.CODE, "x < 1")

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

      val condition = ifStmt.expandCondition
      condition.checkForSingle(NodeKeys.CODE, "x > 0")

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

      val condition = ifStmt.expandCondition
      condition.checkForSingle(NodeKeys.CODE, "x > 0")

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
      val conditionalExpr = call.expandAst(NodeTypes.CALL) //formerly control structure
      conditionalExpr.check(1, _.value2(NodeKeys.CODE), expectations = "(foo == 1) ? bar : 0")
      conditionalExpr.check(1, _.value2(NodeKeys.NAME), expectations = "<operator>.conditionalExpression")
      val params = conditionalExpr.expandAst()
      params.check(3,
                   arg => (arg.value2(NodeKeys.ARGUMENT_INDEX), arg.value2(NodeKeys.CODE)),
                   expectations = (1, "foo == 1"),
                   (2, "bar"),
                   (3, "0"))
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

      val conditionNode = forLoop.expandCondition
      conditionNode.checkForSingle(NodeKeys.CODE, "x < 1")

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

      val fieldAccess = block.expandAst(NodeTypes.CALL)
      fieldAccess.checkForSingle(NodeKeys.NAME, Operators.fieldAccess)

      val arguments = fieldAccess.expandAst(NodeTypes.IDENTIFIER)
      arguments.check(1, arg => {
        (arg.value2(NodeKeys.NAME), arg.value2(NodeKeys.ARGUMENT_INDEX))
      }, expectations = ("x", 1))
      fieldAccess
        .expandAst(NodeTypes.FIELD_IDENTIFIER)
        .check(1, arg => {
          (arg.value2(NodeKeys.CODE), arg.value2(NodeKeys.CANONICAL_NAME), arg.value2(NodeKeys.ARGUMENT_INDEX))
        }, expectations = ("a", "a", 2))

    }

    "be correct for indirect member access" in new Fixture("""
        |void method(struct someUndefinedStruct *x) {
        |  x->a;
        |}
      """.stripMargin) {
      val method = getMethod("method")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val fieldAccess = block.expandAst(NodeTypes.CALL)
      fieldAccess.checkForSingle(NodeKeys.NAME, Operators.indirectFieldAccess)

      val arguments = fieldAccess.expandAst(NodeTypes.IDENTIFIER)
      arguments.check(1, arg => {
        (arg.value2(NodeKeys.NAME), arg.value2(NodeKeys.ARGUMENT_INDEX))
      }, expectations = ("x", 1))
      fieldAccess
        .expandAst(NodeTypes.FIELD_IDENTIFIER)
        .check(1, arg => {
          (arg.value2(NodeKeys.CODE), arg.value2(NodeKeys.CANONICAL_NAME), arg.value2(NodeKeys.ARGUMENT_INDEX))
        }, expectations = ("a", "a", 2))
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
      typeDeclBar.checkForSingle(NodeKeys.FULL_NAME, "bar")
      val memberBar = typeDeclBar.expandAst(NodeTypes.MEMBER)
      memberBar.checkForSingle(NodeKeys.CODE, "y")

      val typeDeclFoo2 = typeDeclBar.expandAst(NodeTypes.TYPE_DECL)
      typeDeclFoo2.checkForSingle(NodeKeys.FULL_NAME, "foo2")
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

    "be correct for single inheritance" in new Fixture(
      """
        |class Base {public: int i;};
        |class Derived : public Base{
        |public:
        | char x;
        | int method(){return i;};
        |};
      """.stripMargin
    ) {

      val derivedL = getTypeDecl("Derived")
      derivedL.checkForSingle()

      val derived = derivedL.head
      derived.value[List[String]](NodeKeys.INHERITS_FROM_TYPE_FULL_NAME.name) shouldBe List("Base")
    }

    "be correct for multiple inheritance" in new Fixture(
      """
        |class OneBase {public: int i;};
        |class TwoBase {public: int j;};
        |
        |class Derived : public OneBase, protected TwoBase{
        |public:
        | char x;
        | int method(){return i;};
        |};
      """.stripMargin
    ) {

      val derivedL = getTypeDecl("Derived")
      derivedL.checkForSingle()

      val derived = derivedL.head
      derived.value[List[String]](NodeKeys.INHERITS_FROM_TYPE_FULL_NAME.name) shouldBe List("OneBase", "TwoBase")
    }

    "be correct for method calls" in new Fixture(
      """
        |void foo(int x) {
        |  bar(x);
        |}
        |""".stripMargin
    ) {
      val call = getCall("bar")
      call.checkForSingle()

      val args = call.expandArgument
      args.checkForSingle(NodeKeys.CODE, "x")
    }

    "be correct for method returns" in new Fixture(
      """
        |void double(int x) {
        |  return x * 2;
        |}
        |""".stripMargin
    ) {
      val method = getMethod("double")
      method.checkForSingle()

      val methodBody = method.expandAst(NodeTypes.BLOCK)
      methodBody.checkForSingle()

      val methodReturn = methodBody.expandAst(NodeTypes.RETURN)
      methodReturn.checkForSingle()

      val args = methodReturn.expandArgument
      args.checkForSingle(NodeKeys.CODE, "x * 2")
    }

    "be correct for binary method calls" in new Fixture(
      """
        |void double(int x) {
        |  return x * 2;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.multiplication")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2[String](NodeKeys.CODE), "x", "2")
    }

    "be correct for unary method calls" in new Fixture(
      """
        |bool invert(bool b) {
        |  return !b;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.logicalNot")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.checkForSingle(NodeKeys.CODE, "b")
    }

    "be correct for post increment method calls" in new Fixture(
      """
        |int foo(int x) {
        |  int sub = x--;
        |  int pos = x++;
        |  return pos;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.postIncrement")
      call.checkForSingle()
      val callArgs = call.expandArgument
      callArgs.checkForSingle(NodeKeys.CODE, "x")

      val callDec = getCall("<operator>.postDecrement")
      callDec.checkForSingle()
      val callArgsDec = callDec.expandArgument
      callArgsDec.checkForSingle(NodeKeys.CODE, "x")
    }

    "be correct for conditional expressions containing calls" in new Fixture(
      """
        |int abs(int x) {
        |  return x > 0 ? x : -x;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.conditionalExpression")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.check(3, x => x.value2[String](NodeKeys.CODE), "x > 0", "x", "-x")
    }

    "be correct for sizeof expressions" in new Fixture(
      """
        |size_t int_size() {
        |  return sizeof(int);
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.sizeOf")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.checkForSingle(NodeKeys.CODE, "int")
    }

    "be correct for label" in new Fixture("foo() { label: }") {
      val jumpTarget = getVertices("label", NodeTypes.JUMP_TARGET)
      jumpTarget.checkForSingle(NodeKeys.CODE, "label:")
    }

    "be correct for array indexing" in new Fixture(
      """
        |int head(int x[]) {
        |  return x[0];
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.indirectIndexAccess")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2[String](NodeKeys.CODE), "x", "0")
    }

    "be correct for type casts" in new Fixture(
      """
        |int trunc(long x) {
        |  return (int) x;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.cast")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2[String](NodeKeys.CODE), "int", "x")
    }

    "be correct for member accesses" in new Fixture(
      """
        |int trunc(Foo x) {
        |  return x.count;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.fieldAccess")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2[String](NodeKeys.CODE), "x", "count")
      callArgs.check(2, x => x.label(), NodeTypes.IDENTIFIER, NodeTypes.FIELD_IDENTIFIER)
      callArgs.check(2, x => {
        if (x.label() == NodeTypes.FIELD_IDENTIFIER) { x.value2[String](NodeKeys.CANONICAL_NAME) } else { "" }
      }, "", "count")
    }

    "be correct for indirect member accesses" in new Fixture(
      """
        |int trunc(Foo* x) {
        |  return x->count;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.indirectFieldAccess")
      call.checkForSingle()

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2[String](NodeKeys.CODE), "x", "count")
      callArgs.check(2, x => x.label(), NodeTypes.IDENTIFIER, NodeTypes.FIELD_IDENTIFIER)
      callArgs.check(2, x => {
        if (x.label() == NodeTypes.FIELD_IDENTIFIER) { x.value2[String](NodeKeys.CANONICAL_NAME) } else { "" }
      }, "", "count")
    }

    "be correct for 'new' array" in new Fixture(
      """
        |int[] alloc(int n) {
        |   int[] arr = new int[n];
        |   return arr;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.new")
      call.checkForSingle(NodeKeys.CODE, "new int[n]")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.value2[String](NodeKeys.CODE), "int")
    }

    "be correct for 'new' object" in new Fixture(
      """
        |Foo* alloc(int n) {
        |   Foo* foo = new Foo(n, 42);
        |   return foo;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.new")
      call.checkForSingle(NodeKeys.CODE, "new Foo(n, 42)")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.value2[String](NodeKeys.CODE), "Foo")
    }

    "be correct for simple 'delete'" in new Fixture(
      """
        |int delete_number(int* n) {
        |  delete n;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.delete")
      call.checkForSingle(NodeKeys.CODE, "delete n")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.value2[String](NodeKeys.CODE), "n")
    }

    "be correct for array 'delete'" in new Fixture(
      """
        |void delete_number(int n[]) {
        |  delete[] n;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.delete")
      call.checkForSingle(NodeKeys.CODE, "delete[] n")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.value2[String](NodeKeys.CODE), "n")
    }

    "be correct for const_cast" in new Fixture(
      """
        |void foo() {
        |  int y = const_cast<int>(n);
        |  return;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.cast")
      call.checkForSingle(NodeKeys.CODE, "const_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2(NodeKeys.CODE), "int", "n")
    }

    "be correct for static_cast" in new Fixture(
      """
        |void foo() {
        |  int y = static_cast<int>(n);
        |  return;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.cast")
      call.checkForSingle(NodeKeys.CODE, "static_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2(NodeKeys.CODE), "int", "n")
    }

    "be correct for dynamic_cast" in new Fixture(
      """
        |void foo() {
        |  int y = dynamic_cast<int>(n);
        |  return;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.cast")
      call.checkForSingle(NodeKeys.CODE, "dynamic_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2(NodeKeys.CODE), "int", "n")
    }

    "be correct for reinterpret_cast" in new Fixture(
      """
        |void foo() {
        |  int y = reinterpret_cast<int>(n);
        |  return;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.cast")
      call.checkForSingle(NodeKeys.CODE, "reinterpret_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.value2(NodeKeys.CODE), "int", "n")
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
