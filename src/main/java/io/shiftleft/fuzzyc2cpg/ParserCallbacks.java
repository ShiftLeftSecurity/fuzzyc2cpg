package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import io.shiftleft.fuzzyc2cpg.cfg.ASTToCFGConverter;
import io.shiftleft.fuzzyc2cpg.cfg.CCFGFactory;
import io.shiftleft.fuzzyc2cpg.cfg.CFG;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CFGNode;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Property;
import io.shiftleft.proto.cpg.Cpg.NodePropertyName;
import io.shiftleft.proto.cpg.Cpg.PropertyValue;

public class ParserCallbacks extends ASTNodeVisitor {

  /**
   * Callback triggered for each function definition
   * */

  public void visit(FunctionDefBase ast) {
    ASTToCFGConverter converter = new ASTToCFGConverter();
    converter.setFactory(new CCFGFactory());
    CFG cfg = converter.convert(ast);
    for (CFGNode cfgNode : cfg.getVertices()) {
      // We can check for instanceof AstNodeContainer here
      // and have .astNode.fullName to get the type.
    }

  }

  public void visit(ClassDefStatement node) {

    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.

    String typeName = node.identifier.toString();
    typeName = typeName.substring(1, typeName.length() -1);

    Property nameProperty = Node.Property.newBuilder()
        .setName(NodePropertyName.NAME)
        .setValue(PropertyValue.newBuilder().setStringValue(typeName).build())
        .build();
    Property fullNameProperty = Node.Property.newBuilder()
        .setName(NodePropertyName.FULL_NAME)
        .setValue(PropertyValue.newBuilder().setStringValue(typeName).build())
        .build();
    Property isExternalProperty = Node.Property.newBuilder()
        .setName(NodePropertyName.IS_EXTERNAL)
        .setValue(PropertyValue.newBuilder().setBoolValue(false).build())
        .build();

    Node typeDeclNode = Node.newBuilder()
        .setKey(IdPool.getNextId())
        .setType(NodeType.TYPE_DECL)
        .addProperty(nameProperty)
        .addProperty(fullNameProperty)
        .addProperty(isExternalProperty)
        .build();

    structureCpg.addNode(typeDeclNode);

    structureCpg.addEdge(CpgStruct.Edge.newBuilder()
        .setType(EdgeType.AST)
        .setSrc(structureCpg.getNamespaceBlockNode().getKey())
        .setDst(typeDeclNode.getKey())
        .build()
    );

  }

  public void visit(IdentifierDeclStatement node) {

  }

}
