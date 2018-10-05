package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.expressions.AssignmentExpression;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Expression;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.FunctionDefBase;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterBase;
import io.shiftleft.fuzzyc2cpg.ast.langc.expressions.CallExpression;
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.Parameter;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.Condition;
import io.shiftleft.fuzzyc2cpg.ast.statements.ExpressionStatement;
import io.shiftleft.fuzzyc2cpg.ast.statements.IdentifierDeclStatement;
import io.shiftleft.fuzzyc2cpg.cfg.ASTToCFGConverter;
import io.shiftleft.fuzzyc2cpg.cfg.CCFGFactory;
import io.shiftleft.fuzzyc2cpg.cfg.CFG;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.ASTNodeContainer;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgEntryNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgErrorNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgExceptionNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgExitNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.CfgNode;
import io.shiftleft.fuzzyc2cpg.cfg.nodes.InfiniteForNode;
import io.shiftleft.fuzzyc2cpg.outputmodules.OutputModule;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Edge.EdgeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Builder;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.Property;
import io.shiftleft.proto.cpg.Cpg.NodePropertyName;
import io.shiftleft.proto.cpg.Cpg.PropertyValue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

public class FunctionDefHandler {

  private final StructureCpg structureCpg;
  HashMap<CfgNode, Node> nodeToProtoNode = new HashMap<>();
  private CFG cfg;
  Node methodNode;
  CpgStruct.Builder bodyCpg;
  private final OutputModule outputModule;

  public FunctionDefHandler(StructureCpg structureCpg, OutputModule outputModule) {
    this.structureCpg = structureCpg;
    this.bodyCpg = CpgStruct.newBuilder();
    this.outputModule = outputModule;
  }

  public void handle(FunctionDefBase ast) {
    initializeCfg(ast);
    addMethodStubToStructureCpg(ast);
    addMethodBodyCpg(cfg);
    String outputFilename = generateOutputFilename(ast);
    outputModule.output(bodyCpg, outputFilename);
  }

  private String generateOutputFilename(FunctionDefBase ast) {
    Path path = Paths.get(Config.outputDirectory, ast.getName() + ".proto");
    return path.toString();
  }

  private void initializeCfg(FunctionDefBase ast) {
    ASTToCFGConverter converter = new ASTToCFGConverter();
    converter.setFactory(new CCFGFactory());
    cfg = converter.convert(ast);
  }


  private void addMethodStubToStructureCpg(FunctionDefBase functionDef) {

    String name = functionDef.getName();

    methodNode = Node.newBuilder()
        .setKey(IdPool.getNextId())
        .setType(NodeType.METHOD)
        .addProperty(newStringProperty(NodePropertyName.NAME, name)).build();

    structureCpg.addNode(methodNode);
    connectMethodToNamespaceAndType(methodNode);

    for (ParameterBase parameter : functionDef.getParameterList()) {
      addParameterCpg(parameter);
    }

  }

  private void connectMethodToNamespaceAndType(Node methodNode) {
    structureCpg.addEdge(
        CpgStruct.Edge.newBuilder().setType(EdgeType.AST)
            .setSrc(structureCpg.getNamespaceBlockNode().getKey())
            .setDst(methodNode.getKey())
            .build()
    );
  }


  private void addParameterCpg(ParameterBase parameter) {

    Property.Builder codeProperty =
        newStringProperty(NodePropertyName.CODE, parameter.getEscapedCodeStr());

    Property.Builder nameProperty = newStringProperty(NodePropertyName.NAME, parameter.getName());

    Property orderProperty = Property
        .newBuilder()
        .setName(NodePropertyName.ORDER)
        .setValue(PropertyValue.newBuilder().setIntValue(parameter.getChildNumber()))
        .build();

    structureCpg.addNode(
        Node.newBuilder()
            .setType(NodeType.METHOD_PARAMETER_IN)
            .addProperty(codeProperty)
            .addProperty(nameProperty)
            .addProperty(orderProperty)
            .build()
    );

    Node evalNode = Node.newBuilder()
        .setType(NodeType.TYPE)
        .addProperty(Property.newBuilder()
            .setName(NodePropertyName.NAME)
            .setValue(PropertyValue.newBuilder().setStringValue(parameter.getType().getEscapedCodeStr())))
        .addProperty(Property.newBuilder().setName(NodePropertyName.FULL_NAME).setValue(
            PropertyValue.newBuilder().setStringValue(parameter.getType().getEscapedCodeStr())))
        .build();

    structureCpg.addNode(evalNode);

  }

  private void addMethodBodyCpg(CFG cfg) {
    addNodes(cfg);
    addEdges(cfg);
  }

  private void addNodes(CFG cfg) {
    for (CfgNode cfgNode : cfg.getVertices()) {
      if (cfgNode instanceof CfgEntryNode) {
        // No need to add the start node. The start nodes
        // corresponds to the method node in the CPG, and
        // that's already present in the structureCpg.
        // However, we do need to add it to the `nodeToProtoNode`
        // map so that the node is present when creating edges
        nodeToProtoNode.put(cfg.getEntryNode(), methodNode);
      } else if (
          cfgNode instanceof CfgErrorNode ||
          cfgNode instanceof CfgExceptionNode ||
          cfgNode instanceof CfgExitNode ||
          cfgNode instanceof InfiniteForNode) {
        addNewTrueLiteralNode(cfgNode);
      } else if (cfgNode instanceof ASTNodeContainer) {
        addStatementNodes(cfgNode);
      }
    }
  }

  private void addNewTrueLiteralNode(CfgNode cfgNode) {
    Property codeProperty = Node.Property.newBuilder()
        .setName(NodePropertyName.NAME)
        .setValue(PropertyValue.newBuilder().setStringValue("<true>").build())
        .build();

    Builder nodeBuilder = Node.newBuilder()
        .setType(NodeType.LITERAL)
        .addProperty(codeProperty);
    nodeToProtoNode.put(cfgNode, nodeBuilder.build());
    bodyCpg.addNode(nodeBuilder);
  }

  private void addStatementNodes(CfgNode cfgNode) {
    assert(cfgNode instanceof ASTNodeContainer);
    ASTNodeContainer container = (ASTNodeContainer) cfgNode;
    AstNode astNode = container.getASTNode();

    if (astNode instanceof Parameter) {
      return;
    } else if ( astNode instanceof ExpressionStatement) {
      ExpressionStatement stmt = (ExpressionStatement) astNode;
      Expression expression = stmt.getExpression();
      addAllNodesOfExpression(expression);
    } else if (astNode instanceof Condition) {
      Condition condition = (Condition) astNode;
      addAllNodesOfExpression(condition.getExpression());
    } else if (astNode instanceof IdentifierDeclStatement) {
      IdentifierDeclStatement stmt = (IdentifierDeclStatement) astNode;
      for (AstNode node : stmt.getIdentifierDeclList()) {
        for (int i = 0; i < node.getChildCount(); i++) {
          AstNode child = node.getChild(i);
          if (child instanceof AssignmentExpression) {
            addAllNodesOfExpression((Expression) child);
          }
        }
      }

    } else {
      System.out.println("Unhandled node type: " + astNode.getClass().getSimpleName());
    }
  }

  private void addAllNodesOfExpression(Expression expression) {
    addNodeForExpressionRoot(expression);

    int childCount = expression.getChildCount();
    for (int i = 0; i < childCount; i++) {
      addAllNodesOfExpression((Expression) expression.getChild(i));
    }
  }

  private void addNodeForExpressionRoot(Expression expression) {

    Node.Builder nodeBuilder = Node.newBuilder();

    if (expression instanceof CallExpression) {
      CallExpression callExpression = (CallExpression) expression;
      Expression targetFunc = callExpression.getTargetFunc();
      String operator = targetFunc.getEscapedCodeStr();
      nodeBuilder.addProperty(newStringProperty(NodePropertyName.NAME, operator));
    }

    bodyCpg.addNode(nodeBuilder);
  }

  Property.Builder newStringProperty(NodePropertyName name, String value) {
    return Property.newBuilder()
        .setName(name)
        .setValue(PropertyValue.newBuilder().setStringValue(value).build());
  }


  private void addEdges(CFG cfg) {

  }



}
