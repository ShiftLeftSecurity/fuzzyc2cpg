package io.shiftleft.fuzzyc2cpg.passes.astcreation

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor
import org.slf4j.LoggerFactory
import io.shiftleft.codepropertygraph.generated.{EdgeTypes, nodes}
import io.shiftleft.codepropertygraph.generated.nodes.NewNode
import io.shiftleft.fuzzyc2cpg.{Defines, Global}
import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement
import io.shiftleft.fuzzyc2cpg.scope.Scope
import io.shiftleft.passes.DiffGraph

import scala.jdk.CollectionConverters._

object AstCreator {}

class AstCreator(diffGraph: DiffGraph.Builder, astParentNode: nodes.NamespaceBlock, global: Global)
    extends ASTNodeVisitor {

  private val logger = LoggerFactory.getLogger(getClass)

  private val scope = new Scope[String, (nodes.CpgNode, String), nodes.CpgNode]()

  private var contextStack = List[Context]()
  pushContext(astParentNode, 1)

  private class Context(val cpgParent: nodes.CpgNode,
                        var childNum: Int,
                        val parentIsClassDef: Boolean,
                        val parentIsMemberAccess: Boolean = false,
                        var addConditionEdgeOnNextAstEdge: Boolean = false,
                        var addArgumentEdgeOnNextAstEdge: Boolean = false) {}

  def convert(astNode: AstNode): Unit = {
    astNode.accept(this)
  }

  override def visit(astClassDef: ClassDefStatement): Unit = {
    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.
    var name = astClassDef.identifier.toString
    name = name.substring(1, name.length - 1)
    val baseClassList = astClassDef.baseClasses.asScala.map { identifier =>
      val baseClassName = identifier.toString
      baseClassName.substring(1, baseClassName.length - 1)
    }.toList

    baseClassList.foreach(registerType)

    val typeDecl = nodes.NewTypeDecl(
      name = name,
      fullName = name,
      isExternal = false,
      inheritsFromTypeFullName = baseClassList
    )

    diffGraph.addNode(typeDecl)
    addAstChild(typeDecl)

    val templateParamList = astClassDef.getTemplateParameterList
    if (templateParamList != null) {
      templateParamList.asScala.foreach { template =>
        template.accept(this)
      }
    }

    pushContext(typeDecl, 1, parentIsClassDef = true)
    astClassDef.content.accept(this)
    popContext()
  }

  override def visit(astBlock: CompoundStatement): Unit = {
    if (context.parentIsClassDef) {
      astBlock.getStatements.asScala.foreach { statement =>
        statement.accept(this)
      }
    } else {
      val cpgBlock = nodes.NewBlock(
        code = "",
        order = context.childNum,
        argumentIndex = context.childNum,
        typeFullName = registerType(Defines.voidTypeName),
        lineNumber = astBlock.getLocation.startLine.map(new Integer(_)),
        columnNumber = astBlock.getLocation.startPos.map(new Integer(_))
      )
      addAstChild(cpgBlock)

      pushContext(cpgBlock, 1)
      scope.pushNewScope(cpgBlock)
      astBlock.getStatements.asScala.foreach { statement =>
        statement.accept(this)
      }
      popContext()
      scope.popScope()
    }
  }

  // Utilities

  private def registerType(typeName: String): String = {
    global.usedTypes += typeName
    typeName
  }

  private def addAstChild(child: NewNode): Unit = {
    diffGraph.addEdge(context.cpgParent, child, EdgeTypes.AST)
    context.childNum += 1
    if (context.addConditionEdgeOnNextAstEdge) {
      diffGraph.addEdge(context.cpgParent, child, EdgeTypes.CONDITION)
      context.addConditionEdgeOnNextAstEdge = false
    }

    if (context.addArgumentEdgeOnNextAstEdge) {
      diffGraph.addEdge(context.cpgParent, child, EdgeTypes.ARGUMENT)
      context.addArgumentEdgeOnNextAstEdge = false
    }
  }

  private def context: Context = {
    contextStack.head
  }

  private def pushContext(cpgParent: nodes.CpgNode,
                          startChildNum: Int,
                          parentIsClassDef: Boolean = false,
                          parentIsMemberAccess: Boolean = false): Unit = {
    contextStack = new Context(cpgParent, startChildNum, parentIsClassDef, parentIsMemberAccess) :: contextStack
  }

  private def popContext(): Unit = {
    contextStack = contextStack.tail
  }

}
