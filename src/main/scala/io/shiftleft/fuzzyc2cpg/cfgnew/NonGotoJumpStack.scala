package io.shiftleft.fuzzyc2cpg.cfgnew

class NonGotoJumpStack[NodeType] {
  private case class StackElement(breaks: Seq[NodeType] = Seq(), continues: Seq[NodeType] = Seq()) {
    def addBreak(break: NodeType): StackElement = {
      StackElement(breaks :+ break, continues)
    }

    def addContinue(continue: NodeType): StackElement = {
      StackElement(breaks, continues :+ continue)
    }
  }

  private var stack = Seq[StackElement]()

  def pushLayer(): Unit = {
    stack = StackElement() +: stack
  }

  def popLayer(): Unit = {
    stack = stack.tail
  }

  def storeBreak(break: NodeType): Unit = {
    stack = stack.head.addBreak(break) +: stack
  }

  def storeContinue(continue: NodeType): Unit = {
    stack = stack.head.addContinue(continue) +: stack
  }

  def getTopBreaks: Seq[NodeType] = {
    stack.head.breaks
  }

  def getTopContinues: Seq[NodeType] = {
    stack.head.continues
  }

}
