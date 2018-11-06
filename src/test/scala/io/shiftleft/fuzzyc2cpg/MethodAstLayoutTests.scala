package io.shiftleft.fuzzyc2cpg

import gremlin.scala._
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, NodeKeys, NodeTypes, Operators}
import org.scalatest.{Matchers, WordSpec}

class MethodAstLayoutTests extends WordSpec with Matchers with TravesalUtils {
  val fixture = CpgTestFixture("methodastlayout")

  implicit class VertexListWrapper(vertexList: List[Vertex]) {
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

    def check[A](count: Int,
                 mapFunc: Vertex => A,
                 expectations: A*): Unit = {
      vertexList.size shouldBe count
      vertexList.map(mapFunc).toSet shouldBe expectations.toSet
    }

  }

  "AST layout" should {
    "be correct for empty method1" in {
      val method = getMethod("method1")
      method.expandAst(NodeTypes.BLOCK).checkForSingle()
    }

    "be correct for decl assignment in method2" in {
      val method = getMethod("method2")
      val block= method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      block.expandAst(NodeTypes.LOCAL).checkForSingle(NodeKeys.NAME, "local")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val arguments = assignment.expandAst()
      arguments.check(2,
        arg =>
          (arg.label,
            arg.value2(NodeKeys.CODE),
            arg.value2(NodeKeys.ORDER),
            arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations =
          (NodeTypes.IDENTIFIER, "local", 1, 1),
        (NodeTypes.LITERAL, "1", 2, 2))
    }


    "be correct for nested expression in method3" in {
      val method = getMethod("method3")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val locals = block.expandAst(NodeTypes.LOCAL)
      locals.check(3, local => local.value2(NodeKeys.NAME),
        expectations = "x", "y", "z")

      val assignment = block.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val rightHandSide = assignment.expandAst(NodeTypes.CALL).filterOrder(2)
      rightHandSide.checkForSingle(NodeKeys.NAME, Operators.addition)

      val arguments = rightHandSide.expandAst()
      arguments.check(2, arg =>
        (arg.label,
          arg.value2(NodeKeys.CODE),
          arg.value2(NodeKeys.ORDER),
          arg.value2(NodeKeys.ARGUMENT_INDEX)),
        expectations = (NodeTypes.IDENTIFIER, "y", 1, 1),
          (NodeTypes.IDENTIFIER, "z", 2, 2)
      )
    }

    "be correct for nested block method4" in {
      val method = getMethod("method4")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val locals = block.expandAst(NodeTypes.LOCAL)
      locals.checkForSingle(NodeKeys.NAME, "x")

      val nestedBlock = block.expandAst(NodeTypes.BLOCK)
      nestedBlock.checkForSingle()
      val nestedLocals = nestedBlock.expandAst(NodeTypes.LOCAL)
      nestedLocals.checkForSingle(NodeKeys.NAME, "y")
    }

    "be correct for while in method5" in {
      val method = getMethod("method5")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()

      val whileStmt = block.expandAst(NodeTypes.UNKNOWN)
      whileStmt.check(1, whileStmt => whileStmt.value2(NodeKeys.PARSER_TYPE_NAME),
        expectations = "WhileStatement")

      val lessThan = whileStmt.expandAst(NodeTypes.CALL)
      lessThan.checkForSingle(NodeKeys.NAME, Operators.lessThan)

      val whileBlock = whileStmt.expandAst(NodeTypes.BLOCK)
      whileBlock.checkForSingle()

      val assignPlus = whileBlock.expandAst(NodeTypes.CALL)
      assignPlus.filterOrder(1).checkForSingle(NodeKeys.NAME, Operators.assignmentPlus)
    }

    "be correct for if in method6" in {
      val method = getMethod("method6")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val ifStmt = block.expandAst(NodeTypes.UNKNOWN)
      ifStmt.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME),
        expectations = "IfStatement")

      val greaterThan = ifStmt.expandAst(NodeTypes.CALL)
      greaterThan.checkForSingle(NodeKeys.NAME, Operators.greaterThan)

      val ifBlock = ifStmt.expandAst(NodeTypes.BLOCK)
      ifBlock.checkForSingle()

      val assignment = ifBlock.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)
    }

    "be correct for if-else in method7" in {
      val method = getMethod("method7")
      val block = method.expandAst(NodeTypes.BLOCK)
      block.checkForSingle()
      val ifStmt = block.expandAst(NodeTypes.UNKNOWN)
      ifStmt.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME),
        expectations = "IfStatement")

      val greaterThan = ifStmt.expandAst(NodeTypes.CALL)
      greaterThan.checkForSingle(NodeKeys.NAME, Operators.greaterThan)

      val ifBlock = ifStmt.expandAst(NodeTypes.BLOCK)
      ifBlock.checkForSingle()

      val assignment = ifBlock.expandAst(NodeTypes.CALL)
      assignment.checkForSingle(NodeKeys.NAME, Operators.assignment)

      val elseStmt = ifStmt.expandAst(NodeTypes.UNKNOWN)
      elseStmt.check(1, _.value2(NodeKeys.PARSER_TYPE_NAME),
        expectations = "ElseStatement")

      val elseBlock = elseStmt.expandAst(NodeTypes.BLOCK)
      elseBlock.checkForSingle()

      val assignmentInElse = elseBlock.expandAst(NodeTypes.CALL)
      assignmentInElse.checkForSingle(NodeKeys.NAME, Operators.assignment)
    }
  }
}
