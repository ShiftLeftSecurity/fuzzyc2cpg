package io.shiftleft.fuzzyc2cpg.parser.functions.builder;

import io.shiftleft.fuzzyc2cpg.ModuleParser.Parameter_declContext;
import io.shiftleft.fuzzyc2cpg.ModuleParser.Parameter_idContext;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.ParameterType;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterBase;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ParameterList;
import io.shiftleft.fuzzyc2cpg.parser.AstNodeFactory;
import io.shiftleft.fuzzyc2cpg.parser.ParseTreeUtils;
import java.util.Stack;
import org.antlr.v4.runtime.ParserRuleContext;

public class ParameterListBuilder extends AstNodeBuilder {

  ParameterList thisItem;

  @Override
  public void createNew(ParserRuleContext ctx) {
    item = new ParameterList();
    thisItem = (ParameterList) item;
    AstNodeFactory.initializeFromContext(thisItem, ctx);
  }

  public void addParameter(Parameter_declContext aCtx,
      Stack<AstNodeBuilder> itemStack) {
    Parameter_declContext ctx = aCtx;
    Parameter_idContext parameter_id = ctx.parameter_id();
    ParameterBase param = AstNodeFactory.create(ctx);

    String baseType = ParseTreeUtils
        .childTokenString(ctx.param_decl_specifiers());
    String completeType = determineCompleteType(parameter_id, baseType);

    ((ParameterType) param.getType()).setBaseType(baseType);
    ((ParameterType) param.getType()).setCompleteType(completeType);

    thisItem.addChild(param);
  }

  public String determineCompleteType(Parameter_idContext parameter_id,
      String baseType) {
    String retType = baseType;

    // TODO: use a string-builder here and clean this up.

    // iterate until nesting level is reached
    // where type is given.

    while (parameter_id.parameter_name() == null) {

      String newCompleteType = "";

      newCompleteType += "(";

      if (parameter_id.ptrs() != null) {
        newCompleteType += ParseTreeUtils
            .childTokenString(parameter_id.ptrs()) + " ";
      }
      if (parameter_id.type_suffix() != null) {
        newCompleteType += ParseTreeUtils
            .childTokenString(parameter_id.type_suffix()) + " ";
      }

      newCompleteType += retType;
      newCompleteType += ")";
      retType = newCompleteType;
      parameter_id = parameter_id.parameter_id();
    }

    if (parameter_id.ptrs() != null) {
      retType += " "
          + ParseTreeUtils.childTokenString(parameter_id.ptrs());
    }
    if (parameter_id.type_suffix() != null) {
      retType += " " + ParseTreeUtils
          .childTokenString(parameter_id.type_suffix());
    }

    return retType;
  }

}
