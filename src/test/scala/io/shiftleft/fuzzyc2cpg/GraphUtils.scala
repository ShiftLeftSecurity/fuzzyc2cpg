package io.shiftleft.fuzzyc2cpg

import gremlin.scala.{ScalaGraph, Vertex}
import io.shiftleft.codepropertygraph.generated.NodeKeys

// little helper for generating dot output
object GraphUtils {

  def getVertexIdStr(v : Vertex) = "v" + v.id()

  val indent = "  "

  def toDot(g : ScalaGraph) : String = {

    var sb = new StringBuilder()

    sb.append("digraph g { rankdir=\"BT\";\n")

    for(v <- g.V.toList()) {
      sb.append(indent + getVertexIdStr(v) +
        " [label=\"" + v.label() + "::" + v.property(NodeKeys.NAME) + "\"];\n")
    }

    for(e <- g.E.toList()) {
      sb.append(indent + getVertexIdStr(e.inVertex()) + " -> "
        + getVertexIdStr(e.outVertex())
        + ";\n")
    }

    sb.append("}")

    return sb.toString()
  }

}
