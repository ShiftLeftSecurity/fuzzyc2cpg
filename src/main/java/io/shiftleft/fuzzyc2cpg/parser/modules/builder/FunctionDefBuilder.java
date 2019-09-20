package io.shiftleft.fuzzyc2cpg.parser.modules.builder;

import java.util.Stack;

import org.antlr.v4.runtime.ParserRuleContext;

import io.shiftleft.fuzzyc2cpg.ModuleParser.*;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.expressions.Identifier;
import io.shiftleft.fuzzyc2cpg.ast.functionDef.ReturnType;
import io.shiftleft.fuzzyc2cpg.ast.langc.functiondef.FunctionDef;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.parser.AstNodeFactory;
import io.shiftleft.fuzzyc2cpg.parser.ParseTreeUtils;
import io.shiftleft.fuzzyc2cpg.parser.functions.builder.ParameterListBuilder;
import io.shiftleft.fuzzyc2cpg.parser.shared.builders.TemplateAstBuilder;

public class FunctionDefBuilder extends TemplateAstBuilder<FunctionDef> {

  private final ParameterListBuilder paramListBuilder = new ParameterListBuilder();

  @Override
  public void createNew(ParserRuleContext ctx) {
    item = new FunctionDef();
    AstNodeFactory.initializeFromContext(item, ctx);
    thisItem = (FunctionDef) item;
  }

  public void setName(Function_nameContext ctx,
      Stack<AstNodeBuilder> itemStack) {
    thisItem.addChild(new Identifier());
    AstNodeFactory.initializeFromContext(thisItem.getIdentifier(), ctx);
  }

  public void setReturnType(Return_typeContext ctx,
      Stack<AstNodeBuilder> itemStack) {
    ReturnType returnType = new ReturnType();
    AstNodeFactory.initializeFromContext(returnType, ctx);
    returnType
        .setBaseType(ParseTreeUtils.childTokenString(ctx.type_name()));
    returnType.setCompleteType(ParseTreeUtils.childTokenString(ctx));
    thisItem.addChild(returnType);
    thisItem.setReturnType(returnType);
  }

  public void setParameterList(Function_param_listContext ctx,
      Stack<AstNodeBuilder> itemStack) {
    paramListBuilder.createNew(ctx);
    thisItem.addChild(paramListBuilder.getItem());
  }

  public void addParameter(Parameter_declContext ctx,
      Stack<AstNodeBuilder> itemStack) {
    paramListBuilder.addParameter(ctx, itemStack);
  }

  public void setContent(CompoundStatement functionContent) {
    thisItem.addChild(functionContent);
  }

  public void setIsOnlyDeclaration(boolean isOnlyDeclaration) {
    thisItem.setIsOnlyDeclaration(isOnlyDeclaration);
  }

}
