package io.shiftleft.fuzzyc2cpg.parser

import io.shiftleft.fuzzyc2cpg.ast.CodeLocation
import org.antlr.v4.runtime.ParserRuleContext

object CodeLocationExtractor {

  def extractFromContext(ctx: ParserRuleContext): CodeLocation = {
    val startLine = Some(ctx.start.getLine)
    val startPos = Some(ctx.start.getCharPositionInLine)
    val startIndex = Some(ctx.start.getStartIndex)
    val endLine = if (ctx.stop != null) { Some(ctx.stop.getLine) } else { None }
    val endIndex = if (ctx.stop != null) { Some(ctx.stop.getStopIndex) } else { None }
    val endPos = if (ctx.stop != null) { Some(ctx.stop.getCharPositionInLine) } else { None }
    new CodeLocation(startLine, startPos, startIndex, endIndex, endLine, endPos)
  }

}
