package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

import io.shiftleft.proto.cpg.Cpg;
import jdk.nashorn.internal.runtime.ParserException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

abstract public class AntlrParserDriver {
  // TODO: This class does two things:
  // * It is a driver for the ANTLRParser, i.e., the parser
  // that creates ParseTrees from Strings. It can also already
  // 'walk' the ParseTree to create ASTs.
  // * It is an AST provider in that it will notify watchers
  // when ASTs are ready.
  // We should split this into two classes.

  public Stack<AstNodeBuilder<? extends AstNode>> builderStack = new Stack<>();
  public TokenSubStream stream;
  public String filename;

  private Parser antlrParser;
  private ParseTreeListener listener;
  private CommonParserContext context = null;

  private List<AntlrParserDriverObserver> observers = new ArrayList<>();
  private Cpg.CpgStruct.Builder cpg = CpgStruct.newBuilder();


  public AntlrParserDriver() {
    super();
  }

  public abstract ParseTree parseTokenStreamImpl(TokenSubStream tokens);

  public abstract Lexer createLexer(CharStream input);

  public void parseAndWalkTokenStream(TokenSubStream tokens)
      throws ParserException {
    filename = "";
    stream = tokens;
    ParseTree tree = parseTokenStream(tokens);
    walkTree(tree);
  }

  public ParseTree parseAndWalkString(String input) throws ParserException {
    ParseTree tree = parseString(input);
    walkTree(tree);
    return tree;
  }

  public ParseTree parseTokenStream(TokenSubStream tokens)
      throws ParserException {
    ParseTree returnTree = parseTokenStreamImpl(tokens);
    if (returnTree == null) {
      throw new ParserException("");
    }
    return returnTree;
  }

  public ParseTree parseString(String input) throws ParserException {
    CharStream inputStream = CharStreams.fromString(input);
    Lexer lex = createLexer(inputStream);
    TokenSubStream tokens = new TokenSubStream(lex);
    ParseTree tree = parseTokenStream(tokens);
    return tree;
  }

  protected void walkTree(ParseTree tree) {
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(getListener(), tree);
  }

  protected boolean isRecognitionException(RuntimeException ex) {

    return ex.getClass() == ParseCancellationException.class
        && ex.getCause() instanceof RecognitionException;
  }

  protected void setLLStarMode(Parser parser) {
    parser.removeErrorListeners();
    // parser.addErrorListener(ConsoleErrorListener.INSTANCE);
    parser.setErrorHandler(new DefaultErrorStrategy());
    // parser.getInterpreter().setPredictionMode(PredictionMode.LL);
  }

  protected void setSLLMode(Parser parser) {
    // parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    parser.removeErrorListeners();
    parser.setErrorHandler(new BailErrorStrategy());
  }

  public void setStack(Stack<AstNodeBuilder<? extends AstNode>> aStack) {
    builderStack = aStack;
  }

  // //////////////////

  public void addObserver(AntlrParserDriverObserver observer) {
    observers.add(observer);
  }

  private void notifyObservers(Consumer<AntlrParserDriverObserver> function) {
    for (AntlrParserDriverObserver observer : observers) {
      function.accept(observer);
    }

  }

  public void begin() {
    notifyObserversOfBegin();
  }

  public void end() {
    notifyObserversOfEnd();
  }

  private void notifyObserversOfBegin() {
    notifyObservers(AntlrParserDriverObserver::begin);
  }

  private void notifyObserversOfEnd() {
    notifyObservers(AntlrParserDriverObserver::end);
  }

  public void notifyObserversOfUnitStart(ParserRuleContext ctx) {
    notifyObservers(new Consumer<AntlrParserDriverObserver>() {
      @Override
      public void accept(AntlrParserDriverObserver observer) {
        observer.startOfUnit(ctx, filename);
      }
    });
  }

  public void notifyObserversOfUnitEnd(ParserRuleContext ctx) {
    notifyObservers(new Consumer<AntlrParserDriverObserver>() {
      @Override
      public void accept(AntlrParserDriverObserver observer) {
        observer.endOfUnit(ctx, filename);
      }
    });
  }

  public void notifyObserversOfItem(AstNode aItem) {
    notifyObservers(new Consumer<AntlrParserDriverObserver>() {
      @Override
      public void accept(AntlrParserDriverObserver observer) {
        observer.processItem(aItem, builderStack);
      }
    });
  }

  public CompoundStatement getResult() {
    return (CompoundStatement) builderStack.peek().getItem();
  }

  public Parser getAntlrParser() {
    return antlrParser;
  }

  public void setAntlrParser(Parser aParser) {
    antlrParser = aParser;
  }

  public ParseTreeListener getListener() {
    return listener;
  }

  public void setListener(ParseTreeListener listener) {
    this.listener = listener;
  }


}