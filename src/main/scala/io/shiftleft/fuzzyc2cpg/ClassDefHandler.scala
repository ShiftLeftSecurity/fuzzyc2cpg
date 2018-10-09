package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.ast.declarations.{ClassDefStatement, IdentifierDecl}
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement
import io.shiftleft.proto.cpg.Cpg
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.{NodeType, Property}
import io.shiftleft.proto.cpg.Cpg.{CpgStruct, NodePropertyName, PropertyValue}

import scala.collection.JavaConverters._

class ClassDefHandler(structureCpg: StructureCpg) {

  def handle(ast: ClassDefStatement): Unit = {
    val typeDeclNode = createTypeDeclNode(ast)
    connectNamespaceAndTypeDecl(typeDeclNode)
    addAndConnectMemberNodes(typeDeclNode, ast)
  }

  private def createTypeDeclNode(ast: ClassDefStatement) = {
    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.
    var typeName = ast.identifier.toString
    typeName = typeName.substring(1, typeName.length - 1)
    val nameProperty = stringProperty(NodePropertyName.NAME, typeName)
    val fullNameProperty = stringProperty(NodePropertyName.FULL_NAME, typeName)

    val isExternalProperty = Property.newBuilder.setName(NodePropertyName.IS_EXTERNAL)
      .setValue(PropertyValue.newBuilder.setBoolValue(false).build)
      .build

    val typeDeclNode = Node.newBuilder.setKey(IdPool.getNextId)
      .setType(NodeType.TYPE_DECL).addProperty(nameProperty)
      .addProperty(fullNameProperty).addProperty(isExternalProperty)
      .build

    structureCpg.addNode(typeDeclNode)
    typeDeclNode
  }

  private def stringProperty(propertyType : Cpg.NodePropertyName, propertyValue : String) = {
    Property.newBuilder
      .setName(propertyType)
      .setValue(PropertyValue.newBuilder
        .setStringValue(propertyValue).build)
      .build
  }

  private def connectNamespaceAndTypeDecl(typeDeclNode: CpgStruct.Node): Unit = {
    structureCpg
      .addEdge(
        edge(EdgeType.AST,
        structureCpg.getNamespaceBlockNode.getKey,
        typeDeclNode.getKey
      ).build)
  }

  private def edge(edgeType : EdgeType, src: Long, dst: Long) =
    CpgStruct.Edge.newBuilder.setType(edgeType)
    .setSrc(src).setDst(dst)

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
      val nameProperty = stringProperty(NodePropertyName.NAME, name)
      val codeProperty = stringProperty(NodePropertyName.CODE, code)
      val memberNode = Node.newBuilder.setKey(IdPool.getNextId)
        .setType(NodeType.MEMBER)
        .addProperty(codeProperty)
        .addProperty(nameProperty)
          .build
      structureCpg.addNode(memberNode)
      structureCpg.addEdge(
        edge(EdgeType.AST, typeDeclNode.getKey, memberNode.getKey).build
      )
    }
  }

  private def children(node: AstNode) =
    (0 to node.getChildCount).map(node.getChild)
      .filterNot(_ == null)
      .toList



}
