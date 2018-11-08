package io.shiftleft.fuzzyc2cpg

import io.shiftleft.fuzzyc2cpg.ast.AstNode
import io.shiftleft.fuzzyc2cpg.parsetreetoast.FunctionContentTestUtil
import io.shiftleft.proto.cpg.Cpg.CpgStruct
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node
import Utils._
import io.shiftleft.fuzzyc2cpg.cfgnew.AstToCfgConverter
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Node.NodeType
import org.scalatest.{Matchers, WordSpec}

class AstToCfgTests extends WordSpec with Matchers {
  private def createAstFromCode(code: String): AstNode = {
    FunctionContentTestUtil.parseAndWalk(code)
  }

  case class Cfg(entry: Node, exit: Node, container: CpgStruct.Builder)

  private def createCfgForCode(code: String): Cfg = {
    val astRoot = FunctionContentTestUtil.parseAndWalk(code)

    val entry = newNode(NodeType.UNKNOWN).build()
    val exit = newNode(NodeType.UNKNOWN).build()
    val container = CpgStruct.newBuilder()
    val cfg = Cfg(entry, exit, container)

    //val astToCfgConverter = new AstToCfgConverter(entry, exit, )
    cfg
  }

}
