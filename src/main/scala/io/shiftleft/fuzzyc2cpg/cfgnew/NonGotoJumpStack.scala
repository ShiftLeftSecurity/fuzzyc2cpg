package io.shiftleft.fuzzyc2cpg.cfgnew

class NonGotoJumpStack[NodeType] {
  private case class StackElement(breaks: List[NodeType] = List(),
                                  continues: List[NodeType] = List()) {
    def addBreak(break: NodeType): StackElement = {
      StackElement(break :: breaks, continues)
    }

    def addContinue(continue: NodeType): StackElement = {
      StackElement(breaks, continue :: continues)
    }
  }

  private var stack = List[StackElement]()

  def pushLayer(): Unit = {
    stack = StackElement() :: stack
  }

  def popLayer(): Unit = {
    stack = stack.tail
  }

  def storeBreak(break: NodeType): Unit = {
    stack = stack.head.addBreak(break) :: stack
  }

  def storeContinue(continue: NodeType): Unit = {
    stack = stack.head.addContinue(continue) :: stack
  }

  def getTopBreaks: List[NodeType] = {
    stack.head.breaks
  }

  def getTopContinues: List[NodeType] = {
    stack.head.continues
  }

}
