package io.shiftleft.fuzzyc2cpg.passes.cfgcreation

import io.shiftleft.codepropertygraph.generated.nodes.CfgNode

class LayeredStack {
  private case class StackElement(elements: List[CfgNode] = List()) {
    def addNode(element: CfgNode): StackElement = {
      StackElement(element :: elements)
    }
  }

  private var stack = List[StackElement]()

  def pushLayer(): Unit = {
    stack = StackElement() :: stack
  }

  def popLayer(): Unit = {
    stack = stack.tail
  }

  def store(node: CfgNode): LayeredStack = {
    stack = stack.head.addNode(node) :: stack.tail
    this
  }

  def getTopElements: List[CfgNode] = {
    stack.head.elements
  }

  def numberOfLayers: Int = {
    stack.size
  }
}
