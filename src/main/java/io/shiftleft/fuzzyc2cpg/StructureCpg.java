package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Builder;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node;

public class StructureCpg {

  CpgStruct.Builder cpg = CpgStruct.newBuilder();
  CpgStruct.Node namespaceBlockNode;

  public void setNamespaceBlockNode(Node namespaceBlockNode) {
    this.namespaceBlockNode = namespaceBlockNode;
    cpg.addNode(namespaceBlockNode);
  }

  public Node getNamespaceBlockNode() {
    return namespaceBlockNode;
  }

  public void addEdge(Edge edge) {
    cpg.addEdge(edge);
  }

  public void addNode(Node node) {
    cpg.addNode(node);
  }

  public Builder getCpg() {
    return cpg;
  }
}
