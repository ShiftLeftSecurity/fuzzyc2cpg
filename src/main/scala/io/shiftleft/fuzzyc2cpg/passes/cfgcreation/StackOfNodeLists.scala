package io.shiftleft.fuzzyc2cpg.passes.cfgcreation

import io.shiftleft.codepropertygraph.generated.nodes.CfgNode

class StackOfNodeLists {
  private case class NodeList(nodes: List[CfgNode] = List()) {
    def addNode(element: CfgNode): NodeList = {
      NodeList(element :: nodes)
    }
  }

  private var stack = List[NodeList]()

  def pushLayer(): Unit = {
    stack = NodeList() :: stack
  }

  def popLayer(): Unit = {
    stack = stack.tail
  }

  def store(node: CfgNode): Unit = {
    stack = stack.head.addNode(node) :: stack.tail
  }

  def getTopElements: List[CfgNode] = {
    stack.head.nodes
  }

  def numberOfLayers: Int = {
    stack.size
  }
}
