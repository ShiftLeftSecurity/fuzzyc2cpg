package io.shiftleft.fuzzyc2cpg.outputmodules;

// Stays alive during the lifetime of the program

import io.shiftleft.fuzzyc2cpg.ast.declarations.ClassDefStatement;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.outputmodules.common.OutModAstNodeVisitor;

public class ProtoAstNodeVisitor extends OutModAstNodeVisitor {

  public void visit(FunctionDefBase node) {
    System.out.println("FunctionDef");
    System.out.println(node);
    ProtoFunctionExporter exporter = new ProtoFunctionExporter();
    exporter.addToDatabaseSafe(node);
  }

  public void visit(ClassDefStatement node) {

  }

  public void visit(IdentifierDeclStatement node) {

  }

  @Override
  protected void addEdgeFromClassToFunc(long dstNodeId, Long classId) {

  }

}
