package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Property;
import io.shiftleft.proto.cpg.Cpg.NodePropertyName;
import io.shiftleft.proto.cpg.Cpg.PropertyValue;

public class ClassDefHandler {

  private final StructureCpg structureCpg;

  public ClassDefHandler(StructureCpg structureCpg) {
    this.structureCpg = structureCpg;
  }

  public void handle(ClassDefStatement ast) {
    // TODO: currently NAME and FULL_NAME are the same, since
    // the parser does not detect C++ namespaces. Change that,
    // once the parser handles namespaces.

    String typeName = ast.identifier.toString();
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
  }
}
