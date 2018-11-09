package io.shiftleft.fuzzyc2cpg.cfgnew

class LayeredStack[ElementType] {
  private case class StackElement(elements: List[ElementType] = List()) {
    def addNode(element: ElementType): StackElement = {
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

  def store(node: ElementType): Unit = {
    stack = stack.head.addNode(node) :: stack.tail
  }

  def getTopElements: List[ElementType] = {
    stack.head.elements
  }
}
