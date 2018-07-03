package io.shiftleft.fuzzyc2cpg.ast;

import io.shiftleft.fuzzyc2cpg.ast.expressions.Expression;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTNodeVisitor;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class AstNode {

  protected LinkedList<AstNode> children;
  protected int childNumber;
  private Map<String, String> properties;
  private CodeLocation location = new CodeLocation();
  private boolean isInCFG = false;


  /* constructors */

  public AstNode() {
  }

  public AstNode(AstNode otherNode) {
    copyAttributes(otherNode);
    copyChildren(otherNode);
  }


  /* private helper methods */

  private void copyAttributes(AstNode otherNode) {
    setCodeStr(otherNode.getCodeStr());
    location = otherNode.location;
    setChildNumber(otherNode.childNumber);
    if (otherNode.isInCFG()) {
      markAsCFGNode();
    }
  }

  private void copyChildren(AstNode otherNode) {
    if (otherNode.children != null) {
      for (AstNode n : otherNode.children) {
        addChild(new AstNode(n));
      }
    }
  }


  /* methods for handling children */

  public void addChild(AstNode node) {
    if (children == null) {
      children = new LinkedList<AstNode>();
    }
    node.setChildNumber(children.size());
    children.add(node);
  }

  public int getChildCount() {
    if (children == null) {
      return 0;
    }
    return children.size();
  }

  public boolean isLeaf() {
    return (children.size() == 0);
  }

  public AstNode getChild(int i) {
    if (children == null) {
      return null;
    }

    AstNode retval;
    try {
      retval = children.get(i);
    } catch (IndexOutOfBoundsException ex) {
      return null;
    }
    return retval;
  }

  public AstNode popLastChild() {
    return children.removeLast();
  }


  /* getters and setters */

  public String getProperty(String key) {
    if (properties == null) {
      return null;
    }

    String retval = properties.get(key);
    if (retval == null) {
      return null;
    }
    return retval;
  }

  public void setProperty(String key, String val) {
    if (properties == null) {
      properties = new HashMap<String, String>();
    }

    properties.put(key, val);
  }

  public String getFlags() {
    return getProperty(AstNodeProperties.FLAGS);
  }

  public void setFlags(String flags) {
    setProperty(AstNodeProperties.FLAGS, flags);
  }

  public int getChildNumber() {
    return this.childNumber;
  }

  public void setChildNumber(int num) {
    this.childNumber = num;
  }

  public String getEscapedCodeStr() {
    return getCodeStr();
  }

  protected String getCodeStr() {
    return getProperty(AstNodeProperties.CODE);
  }

  public void setCodeStr(String aCodeStr) {
    setProperty(AstNodeProperties.CODE, aCodeStr);
  }

  public Long getNodeId() {
    Long id;
    try {
      id = Long.parseLong(getProperty(AstNodeProperties.NODE_ID));
    } catch (NumberFormatException e) {
      id = -1l;
      System.err
          .println("Trying to retrieve node for node " + super.toString() + ", but none is set " +
              "(type = " + getTypeAsString() + ", location = " + getLocation() + ", code = "
              + getCodeStr() + ")");
      e.printStackTrace();
    }
    return id;
  }

  public void setNodeId(Long id) {
    setProperty(AstNodeProperties.NODE_ID, Long.toString(id));
  }

  public String getLocationString() {
    return this.location.toString();
  }

  public CodeLocation getLocation() {
    return this.location;
  }

  public void setLocation(CodeLocation location) {
    this.location = location;
  }

  public String getTypeAsString() {
    return this.getClass().getSimpleName();
  }

  public String getFullTypeName() {
    return this.getClass().getName();
  }


  /* special methods */

  public String getOperatorCode() {
    if (Expression.class.isAssignableFrom(this.getClass())) {
      return ((Expression) this).getOperator();
    }
    return null;
  }

  public void accept(ASTNodeVisitor visitor) {
    visitor.visit(this);
  }

  public void markAsCFGNode() {
    isInCFG = true;
  }

  public boolean isInCFG() {
    return isInCFG;
  }


  /* overrides */

  @Override
  public String toString() {
    if (null != getEscapedCodeStr() && null != getProperty(AstNodeProperties.NODE_ID)) {
      return "[(" + getNodeId() + ") " + getEscapedCodeStr() + "]";
    }
    if (null != getEscapedCodeStr()) {
      return "[" + getEscapedCodeStr() + "]";
    }
    if (null != getProperty(AstNodeProperties.NODE_ID)) {
      return "[(" + getNodeId() + ") " + getTypeAsString() + "]";
    }

    return super.toString();
  }
}
