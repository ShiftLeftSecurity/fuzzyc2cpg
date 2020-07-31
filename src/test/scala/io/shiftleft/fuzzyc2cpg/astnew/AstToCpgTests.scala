package io.shiftleft.fuzzyc2cpg.astnew

import io.shiftleft.OverflowDbTestInstance
import org.antlr.v4.runtime.{CharStreams, ParserRuleContext}
import org.scalatest.{Matchers, WordSpec}
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeKeysOdb, NodeTypes, Operators}
import io.shiftleft.fuzzyc2cpg.{Global, ModuleLexer}
import io.shiftleft.fuzzyc2cpg.adapter.CpgAdapter
import io.shiftleft.fuzzyc2cpg.adapter.EdgeKind.EdgeKind
import io.shiftleft.fuzzyc2cpg.adapter.EdgeProperty.EdgeProperty
import io.shiftleft.fuzzyc2cpg.adapter.NodeKind.NodeKind
import io.shiftleft.fuzzyc2cpg.adapter.NodeProperty.NodeProperty
import io.shiftleft.fuzzyc2cpg.ast.{AstNode, AstNodeBuilder}
import io.shiftleft.fuzzyc2cpg.parser.modules.AntlrCModuleParserDriver
import io.shiftleft.fuzzyc2cpg.parser.{AntlrParserDriverObserver, TokenSubStream}
import overflowdb._
import overflowdb.traversal._

class AstToCpgTests extends WordSpec with Matchers {

  private class GraphAdapter(graph: OdbGraph) extends CpgAdapter[Node, Node, OdbEdge, OdbEdge] {
    override def createNodeBuilder(kind: NodeKind): Node = {
      graph.addNode(kind.toString)
    }

    override def createNode(vertex: Node, origAstNode: AstNode): Node = {
      vertex
    }

    override def createNode(vertex: Node): Node = {
      vertex
    }

    override def addNodeProperty(vertex: Node, property: NodeProperty, value: String): Unit = {
      vertex.property(property.toString, value)
    }

    override def addNodeProperty(vertex: Node, property: NodeProperty, value: Int): Unit = {
      vertex.property(property.toString, value)
    }

    override def addNodeProperty(vertex: Node, property: NodeProperty, value: Boolean): Unit = {
      vertex.property(property.toString, value)
    }

    override def addNodeProperty(vertex: Node, property: NodeProperty, value: List[String]): Unit = {
      vertex.property(property.toString, value)
    }

    override def createEdgeBuilder(dst: Node, src: Node, edgeKind: EdgeKind): OdbEdge = {
      src.addEdge2(edgeKind.toString, dst)
    }

    override def createEdge(edge: OdbEdge): OdbEdge = {
      edge
    }

    // Not used in test with this adapter.
    override def addEdgeProperty(edgeBuilder: OdbEdge, property: EdgeProperty, value: String): Unit = ???
    override def mapNode(astNode: AstNode): Node = ???
  }

  private implicit class VertexListWrapper(vertexList: List[Node]) {
    def expandAst(filterLabels: String*): List[Node] = {
      if (filterLabels.nonEmpty) {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).hasLabel(filterLabels.head, filterLabels.tail: _*).l)
      } else {
        vertexList.flatMap(_.start.out(EdgeTypes.AST).l)
      }
    }

    def expandCondition: List[Node] =
      vertexList.flatMap(_.start.out(EdgeTypes.CONDITION).l)

    def expandArgument: List[Node] =
      vertexList.flatMap(_.start.out(EdgeTypes.ARGUMENT).l)

    def filterOrder(order: Int): List[Node] = {
      vertexList.filter(_.property(NodeKeysOdb.ORDER) == order)
    }

    def checkForSingle[T](propertyName: PropertyKey[T], value: T): Unit = {
      vertexList.size shouldBe 1
      vertexList.head.property(propertyName) shouldBe value
    }

    def checkForSingle(): Unit = {
      vertexList.size shouldBe 1
    }

    def check[A](count: Int, mapFunc: Node => A, expectations: A*): Unit = {
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

    val graph: OdbGraph = OverflowDbTestInstance.create
    private val astParentNode = graph.addNode("NAMESPACE_BLOCK")
    protected val astParent = List(astParentNode)
    private val cpgAdapter = new GraphAdapter(graph)

    val global = Global()
    nodes.foreach { node =>
      val astToProtoConverter = new AstToCpgConverter(astParentNode, cpgAdapter, global)
      astToProtoConverter.convert(node)
    }

    def getMethod(name: String): List[Node] =
      getVertices(name, NodeTypes.METHOD)

    def getTypeDecl(name: String): List[Node] =
      getVertices(name, NodeTypes.TYPE_DECL)

    def getCall(name: String): List[Node] =
      getVertices(name, NodeTypes.CALL)

    def getVertices(name: String, nodeType: String): List[Node] = {
      val result = graph.nodes(nodeType).has(NodeKeysOdb.NAME -> name).toList

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
        .checkForSingle(NodeKeysOdb.TYPE_FULL_NAME, "void")

      method
        .expandAst(NodeTypes.METHOD_PARAMETER_IN)
        .checkForSingle(NodeKeysOdb.TYPE_FULL_NAME, "int")
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
      local.checkForSingle(NodeKeysOdb.NAME, "local")
      local.checkForSingle(NodeKeysOdb.TYPE_FULL_NAME, "int")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)

      val arguments = assignment.expandAst()
      arguments.check(
        2,
        arg =>
          (arg.label,
           arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.TYPE_FULL_NAME),
           arg.property(NodeKeysOdb.ORDER),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX)),
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
      local.checkForSingle(NodeKeysOdb.NAME, "local")
      local.checkForSingle(NodeKeysOdb.TYPE_FULL_NAME, "int")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)

      val arguments = assignment.expandAst()
      arguments.check(
        2,
        arg =>
          (arg.label,
           arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.TYPE_FULL_NAME),
           arg.property(NodeKeysOdb.ORDER),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX)),
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
          local => (local.label, local.property(NodeKeysOdb.CODE), local.property(NodeKeysOdb.TYPE_FULL_NAME)),
          expectations = (NodeTypes.LOCAL, "local", "int"),
          (NodeTypes.LOCAL, "local2", "int")
        )

      val assignment1 = block.expandAst(NodeTypes.CALL).filterOrder(1)
      assignment1.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)

      val arguments1 = assignment1.expandAst()
      arguments1.check(
        2,
        arg =>
          (arg.label,
           arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.TYPE_FULL_NAME),
           arg.property(NodeKeysOdb.ORDER),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "local", "int", 1, 1),
        (NodeTypes.IDENTIFIER, "x", "int", 2, 2)
      )

      val assignment2 = block.expandAst(NodeTypes.CALL).filterOrder(2)
      assignment2.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)

      val arguments2 = assignment2.expandAst()
      arguments2.check(
        2,
        arg =>
          (arg.label,
           arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.TYPE_FULL_NAME),
           arg.property(NodeKeysOdb.ORDER),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX)),
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
      locals.check(3, local => local.property(NodeKeysOdb.NAME), expectations = "x", "y", "z")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)

      val rightHandSide = assignment.expandAst(NodeTypes.CALL).filterOrder(2)
      rightHandSide.checkForSingle(NodeKeysOdb.NAME, Operators.addition)

      val arguments = rightHandSide.expandAst()
      arguments.check(
        2,
        arg =>
          (arg.label,
           arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.ORDER),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX)),
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
      locals.checkForSingle(NodeKeysOdb.NAME, "x")

      val nestedBlock = block.expandAst(NodeTypes.BLOCK)
      nestedBlock.checkForSingle()
      val nestedLocals = nestedBlock.expandAst(NodeTypes.LOCAL)
      nestedLocals.checkForSingle(NodeKeysOdb.NAME, "y")
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
      whileStmt.check(1, _.property(NodeKeysOdb.CODE), expectations = "while (x < 1)")
      whileStmt.check(1, whileStmt => whileStmt.property(NodeKeysOdb.PARSER_TYPE_NAME), expectations = "WhileStatement")

      val condition = whileStmt.expandCondition
      condition.checkForSingle(NodeKeysOdb.CODE, "x < 1")

      val lessThan = whileStmt.expandAst(NodeTypes.CALL)
      lessThan.checkForSingle(NodeKeysOdb.NAME, Operators.lessThan)

      val whileBlock = whileStmt.expandAst(NodeTypes.BLOCK)
      whileBlock.checkForSingle()

      val assignPlus = whileBlock.expandAst(NodeTypes.CALL)
      assignPlus.filterOrder(1).checkForSingle(NodeKeysOdb.NAME, Operators.assignmentPlus)
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
      ifStmt.check(1, _.property(NodeKeysOdb.PARSER_TYPE_NAME), expectations = "IfStatement")

      val condition = ifStmt.expandCondition
      condition.checkForSingle(NodeKeysOdb.CODE, "x > 0")

      val greaterThan = ifStmt.expandAst(NodeTypes.CALL)
      greaterThan.checkForSingle(NodeKeysOdb.NAME, Operators.greaterThan)

      val ifBlock = ifStmt.expandAst(NodeTypes.BLOCK)
      ifBlock.checkForSingle()

      val assignment = ifBlock.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)
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
      ifStmt.check(1, _.property(NodeKeysOdb.PARSER_TYPE_NAME), expectations = "IfStatement")

      val condition = ifStmt.expandCondition
      condition.checkForSingle(NodeKeysOdb.CODE, "x > 0")

      val greaterThan = ifStmt.expandAst(NodeTypes.CALL)
      greaterThan.checkForSingle(NodeKeysOdb.NAME, Operators.greaterThan)

      val ifBlock = ifStmt.expandAst(NodeTypes.BLOCK)
      ifBlock.checkForSingle()

      val assignment = ifBlock.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)

      val elseStmt = ifStmt.expandAst(NodeTypes.CONTROL_STRUCTURE)
      elseStmt.check(1, _.property(NodeKeysOdb.PARSER_TYPE_NAME), expectations = "ElseStatement")
      elseStmt.check(1, _.property(NodeKeysOdb.CODE), "else")

      val elseBlock = elseStmt.expandAst(NodeTypes.BLOCK)
      elseBlock.checkForSingle()

      val assignmentInElse = elseBlock.expandAst(NodeTypes.CALL)
      assignmentInElse.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)
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
      conditionalExpr.check(1, _.property(NodeKeysOdb.CODE), expectations = "(foo == 1) ? bar : 0")
      conditionalExpr.check(1, _.property(NodeKeysOdb.NAME), expectations = "<operator>.conditionalExpression")
      val params = conditionalExpr.expandAst()
      params.check(3,
                   arg => (arg.property(NodeKeysOdb.ARGUMENT_INDEX), arg.property(NodeKeysOdb.CODE)),
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
      forLoop.check(1, _.property(NodeKeysOdb.PARSER_TYPE_NAME), expectations = "ForStatement")
      forLoop.check(1, _.property(NodeKeysOdb.CODE), expectations = "for ( x = 0, y = 0; x < 1; x += 1)")

      val conditionNode = forLoop.expandCondition
      conditionNode.checkForSingle(NodeKeysOdb.CODE, "x < 1")

      val initBlock = forLoop.expandAst(NodeTypes.BLOCK).filterOrder(1)
      initBlock.checkForSingle()

      val assignments = initBlock.expandAst(NodeTypes.CALL)
      assignments.check(2, _.property(NodeKeysOdb.NAME), expectations = Operators.assignment)

      val condition = forLoop.expandAst(NodeTypes.CALL).filterOrder(2)
      condition.checkForSingle(NodeKeysOdb.NAME, Operators.lessThan)

      val increment = forLoop.expandAst(NodeTypes.CALL).filterOrder(3)
      increment.checkForSingle(NodeKeysOdb.NAME, Operators.assignmentPlus)

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
      plusCall.checkForSingle(NodeKeysOdb.NAME, Operators.plus)

      val identifierX = plusCall.expandAst(NodeTypes.IDENTIFIER)
      identifierX.checkForSingle(NodeKeysOdb.NAME, "x")
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
      plusCall.checkForSingle(NodeKeysOdb.NAME, Operators.preIncrement)

      val identifierX = plusCall.expandAst(NodeTypes.IDENTIFIER)
      identifierX.checkForSingle(NodeKeysOdb.NAME, "x")
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
      call.checkForSingle(NodeKeysOdb.NAME, "foo")

      val argumentX = call.expandAst(NodeTypes.IDENTIFIER)
      argumentX.checkForSingle(NodeKeysOdb.NAME, "x")
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
      fieldAccess.checkForSingle(NodeKeysOdb.NAME, Operators.fieldAccess)

      val arguments = fieldAccess.expandAst(NodeTypes.IDENTIFIER)
      arguments.check(1, arg => {
        (arg.property(NodeKeysOdb.NAME), arg.property(NodeKeysOdb.ARGUMENT_INDEX))
      }, expectations = ("x", 1))
      fieldAccess
        .expandAst(NodeTypes.FIELD_IDENTIFIER)
        .check(1, arg => {
          (arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.CANONICAL_NAME),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX))
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
      fieldAccess.checkForSingle(NodeKeysOdb.NAME, Operators.indirectFieldAccess)

      val arguments = fieldAccess.expandAst(NodeTypes.IDENTIFIER)
      arguments.check(1, arg => {
        (arg.property(NodeKeysOdb.NAME), arg.property(NodeKeysOdb.ARGUMENT_INDEX))
      }, expectations = ("x", 1))
      fieldAccess
        .expandAst(NodeTypes.FIELD_IDENTIFIER)
        .check(1, arg => {
          (arg.property(NodeKeysOdb.CODE),
           arg.property(NodeKeysOdb.CANONICAL_NAME),
           arg.property(NodeKeysOdb.ARGUMENT_INDEX))
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
      sizeof.checkForSingle(NodeKeysOdb.NAME, Operators.sizeOf)

      val arguments = sizeof.expandAst(NodeTypes.IDENTIFIER)
      arguments.checkForSingle(NodeKeysOdb.NAME, "a")
      arguments.checkForSingle(NodeKeysOdb.ARGUMENT_INDEX, new Integer(1))
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
      sizeof.checkForSingle(NodeKeysOdb.NAME, Operators.sizeOf)

      val arguments = sizeof.expandAst(NodeTypes.IDENTIFIER)
      arguments.checkForSingle(NodeKeysOdb.NAME, "a")
      arguments.checkForSingle(NodeKeysOdb.ARGUMENT_INDEX, new Integer(1))
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
      sizeof.checkForSingle(NodeKeysOdb.NAME, Operators.sizeOf)

      // For us it is undecidable whether "int" is a type or an Identifier
      // Thus the implementation always goes for Identifier which we encode
      // here in the tests.
      val arguments = sizeof.expandAst(NodeTypes.IDENTIFIER)
      arguments.checkForSingle(NodeKeysOdb.NAME, "int")
      arguments.checkForSingle(NodeKeysOdb.ARGUMENT_INDEX, new Integer(1))
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
      member.checkForSingle(NodeKeysOdb.CODE, "x")
      member.checkForSingle(NodeKeysOdb.NAME, "x")
      member.checkForSingle(NodeKeysOdb.TYPE_FULL_NAME, "int")
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
      member.check(3, member => member.property(NodeKeysOdb.CODE), expectations = "x", "y", "z")
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
      memberFoo.checkForSingle(NodeKeysOdb.CODE, "x")

      val typeDeclBar = typeDeclFoo.expandAst(NodeTypes.TYPE_DECL)
      typeDeclBar.checkForSingle(NodeKeysOdb.FULL_NAME, "bar")
      val memberBar = typeDeclBar.expandAst(NodeTypes.MEMBER)
      memberBar.checkForSingle(NodeKeysOdb.CODE, "y")

      val typeDeclFoo2 = typeDeclBar.expandAst(NodeTypes.TYPE_DECL)
      typeDeclFoo2.checkForSingle(NodeKeysOdb.FULL_NAME, "foo2")
      val memberFoo2 = typeDeclFoo2.expandAst(NodeTypes.MEMBER)
      memberFoo2.checkForSingle(NodeKeysOdb.CODE, "z")
    }

    "be correct for typedef" in new Fixture(
      """
        |typedef struct foo {
        |} abc;
      """.stripMargin
    ) {
      val aliasTypeDecl = getTypeDecl("abc")

      aliasTypeDecl.checkForSingle(NodeKeysOdb.FULL_NAME, "abc")
      aliasTypeDecl.checkForSingle(NodeKeysOdb.ALIAS_TYPE_FULL_NAME, "foo")
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
      args.checkForSingle(NodeKeysOdb.CODE, "x")
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
      args.checkForSingle(NodeKeysOdb.CODE, "x * 2")
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
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "x", "2")
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
      callArgs.checkForSingle(NodeKeysOdb.CODE, "b")
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
      callArgs.checkForSingle(NodeKeysOdb.CODE, "x")

      val callDec = getCall("<operator>.postDecrement")
      callDec.checkForSingle()
      val callArgsDec = callDec.expandArgument
      callArgsDec.checkForSingle(NodeKeysOdb.CODE, "x")
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
      callArgs.check(3, x => x.property(NodeKeysOdb.CODE), "x > 0", "x", "-x")
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
      callArgs.checkForSingle(NodeKeysOdb.CODE, "int")
    }

    "be correct for label" in new Fixture("foo() { label: }") {
      val jumpTarget = getVertices("label", NodeTypes.JUMP_TARGET)
      jumpTarget.checkForSingle(NodeKeysOdb.CODE, "label:")
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
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "x", "0")
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
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "int", "x")
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
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "x", "count")
      callArgs.check(2, x => x.label(), NodeTypes.IDENTIFIER, NodeTypes.FIELD_IDENTIFIER)
      callArgs.check(2, x => {
        if (x.label() == NodeTypes.FIELD_IDENTIFIER) { x.property(NodeKeysOdb.CANONICAL_NAME) } else { "" }
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
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "x", "count")
      callArgs.check(2, x => x.label(), NodeTypes.IDENTIFIER, NodeTypes.FIELD_IDENTIFIER)
      callArgs.check(2, x => {
        if (x.label() == NodeTypes.FIELD_IDENTIFIER) { x.property(NodeKeysOdb.CANONICAL_NAME) } else { "" }
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
      call.checkForSingle(NodeKeysOdb.CODE, "new int[n]")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.property(NodeKeysOdb.CODE), "int")
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
      call.checkForSingle(NodeKeysOdb.CODE, "new Foo(n, 42)")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.property(NodeKeysOdb.CODE), "Foo")
    }

    "be correct for simple 'delete'" in new Fixture(
      """
        |int delete_number(int* n) {
        |  delete n;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.delete")
      call.checkForSingle(NodeKeysOdb.CODE, "delete n")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.property(NodeKeysOdb.CODE), "n")
    }

    "be correct for array 'delete'" in new Fixture(
      """
        |void delete_number(int n[]) {
        |  delete[] n;
        |}
        |""".stripMargin
    ) {
      val call = getCall("<operator>.delete")
      call.checkForSingle(NodeKeysOdb.CODE, "delete[] n")

      val callArgs = call.expandArgument
      callArgs.check(1, x => x.property(NodeKeysOdb.CODE), "n")
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
      call.checkForSingle(NodeKeysOdb.CODE, "const_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "int", "n")
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
      call.checkForSingle(NodeKeysOdb.CODE, "static_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "int", "n")
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
      call.checkForSingle(NodeKeysOdb.CODE, "dynamic_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "int", "n")
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
      call.checkForSingle(NodeKeysOdb.CODE, "reinterpret_cast<int>(n)")

      val callArgs = call.expandArgument
      callArgs.check(2, x => x.property(NodeKeysOdb.CODE), "int", "n")
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
      method.checkForSingle(NodeKeysOdb.LINE_NUMBER, 6: Integer)

      val block = method.expandAst(NodeTypes.BLOCK)

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeysOdb.NAME, Operators.assignment)
      assignment.checkForSingle(NodeKeysOdb.LINE_NUMBER, 8: Integer)
    }
  }
}
