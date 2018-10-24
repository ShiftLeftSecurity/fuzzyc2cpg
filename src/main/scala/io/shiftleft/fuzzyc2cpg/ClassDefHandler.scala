package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.declarations.{ClassDefStatement, IdentifierDecl}
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.proto.cpg.Cpg
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}
import io.shiftleft.fuzzyc2cpg.Utils.{children, newStringProperty, newEdge}

import scala.collection.JavaConverters._

class ClassDefHandler(structureCpg: CpgStruct.Builder,
                      astParentNode: Node) {

  def handle(ast: ClassDefStatement): Unit = {
    val typeDeclNode = createTypeDeclNode(ast)
    addAndConnectMemberNodes(typeDeclNode, ast)
  }

  private def createTypeDeclNode(ast: ClassDefStatement) = {
    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.
    var typeName = ast.identifier.toString
    typeName = typeName.substring(1, typeName.length - 1)
    val nameProperty = newStringProperty(NodePropertyName.NAME, typeName)
    val fullNameProperty = newStringProperty(NodePropertyName.FULL_NAME, typeName)

    val isExternalProperty = Property.newBuilder.setName(NodePropertyName.IS_EXTERNAL)
      .setValue(PropertyValue.newBuilder.setBoolValue(false).build)
      .build

    val typeDeclNode = Node.newBuilder
      .setKey(IdPool.getNextId)
      .setType(NodeType.TYPE_DECL)
      .addProperty(nameProperty)
      .addProperty(fullNameProperty)
      .addProperty(isExternalProperty)
      /*
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_TYPE, astParentNode.getType))
      .addProperty(newStringProperty(NodePropertyName.AST_PARENT_FULL_NAME,
        astParentNode.getPropertyList.asScala.find(_.getName == NodePropertyName.FULL_NAME)
          .get.getValue.getStringValue))
          */
      .build

    structureCpg.addNode(typeDeclNode)
    typeDeclNode
  }

  def addAndConnectMemberNodes(typeDeclNode: Node, ast: ClassDefStatement) = {

    val nameTypeCodeTuples : List[(String, String, String)] =
      ast.content.getStatements.asScala
        .collect { case declStmt : IdentifierDeclStatement => {
            val typeName = declStmt.getType.baseType
            val memberCodeAndNames = children(declStmt)
              .collect { case decl : IdentifierDecl =>
                (decl.getEscapedCodeStr,
                decl.getName.getEscapedCodeStr)
            }
            memberCodeAndNames.map{ case (code, name) => (code, name, typeName) }
          }
        }.flatten.toList

    nameTypeCodeTuples.foreach{ case (code, name, typeName) =>
      val nameProperty = newStringProperty(NodePropertyName.NAME, name)
      val codeProperty = newStringProperty(NodePropertyName.CODE, code)
      val memberNode = Node.newBuilder.setKey(IdPool.getNextId)
        .setType(NodeType.MEMBER)
        .addProperty(codeProperty)
        .addProperty(nameProperty)
          .build
      structureCpg.addNode(memberNode)
      structureCpg.addEdge(newEdge(EdgeType.AST, memberNode, typeDeclNode))
    }
  }

}
