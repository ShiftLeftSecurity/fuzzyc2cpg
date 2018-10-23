package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import io.shiftleft.fuzzyc2cpg.ast.walking.ASTWalkerEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import jdk.nashorn.internal.runtime.ParserException;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
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

  public Stack<AstNodeBuilder> builderStack = new Stack<AstNodeBuilder>();
  public TokenSubStream stream;
  public String filename;

  private Parser antlrParser;
  private ParseTreeListener listener;
  private CommonParserContext context = null;

  private List<AntlrParserDriverObserver> observers = new ArrayList<>();

  public AntlrParserDriver() {
    super();
  }

  public abstract ParseTree parseTokenStreamImpl(TokenSubStream tokens);

  public abstract Lexer createLexer(ANTLRInputStream input);

  public void parseAndWalkFile(String filename) throws ParserException {
    TokenSubStream stream = createTokenStreamFromFile(filename);
    initializeContextWithFile(filename, stream);

    ParseTree tree = parseTokenStream(stream);
    walkTree(tree);
  }

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
    char[] charArray = input.toCharArray();
    ANTLRInputStream inputStream = new ANTLRInputStream(charArray,
        charArray.length);
    Lexer lex = createLexer(inputStream);
    TokenSubStream tokens = new TokenSubStream(lex);
    ParseTree tree = parseTokenStream(tokens);
    return tree;
  }

  protected TokenSubStream createTokenStreamFromFile(String filename)
      throws ParserException {

    ANTLRInputStream input;
    try {
      input = new ANTLRFileStream(filename);
    } catch (IOException exception) {
      throw new ParserException("");
    }

    Lexer lexer = createLexer(input);
    TokenSubStream tokens = new TokenSubStream(lexer);
    return tokens;

  }

  protected void walkTree(ParseTree tree) {
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(getListener(), tree);
  }

  protected void initializeContextWithFile(String filename,
      TokenSubStream stream) {
    setContext(new CommonParserContext());
    getContext().filename = filename;
    getContext().stream = stream;
    initializeContext(getContext());
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

  public void initializeContext(CommonParserContext context) {
    filename = context.filename;
    stream = context.stream;
  }

  public void setStack(Stack<AstNodeBuilder> aStack) {
    builderStack = aStack;
  }

  // //////////////////

  public void addObserver(AntlrParserDriverObserver observer) {
    observers.add(observer);
  }

  private void notifyObservers(ASTWalkerEvent event) {
    for (AntlrParserDriverObserver observer : observers) {
      observer.update(event);
    }

  }

  public void begin() {
    notifyObserversOfBegin();
  }

  public void end() {
    notifyObserversOfEnd();
  }

  private void notifyObserversOfBegin() {
    ASTWalkerEvent event = new ASTWalkerEvent(ASTWalkerEvent.eventID.BEGIN);
    notifyObservers(event);
  }

  private void notifyObserversOfEnd() {
    ASTWalkerEvent event = new ASTWalkerEvent(ASTWalkerEvent.eventID.END);
    notifyObservers(event);
  }

  public void notifyObserversOfUnitStart(ParserRuleContext ctx) {
    ASTWalkerEvent event = new ASTWalkerEvent(
        ASTWalkerEvent.eventID.START_OF_UNIT);
    event.ctx = ctx;
    event.filename = filename;
    notifyObservers(event);
  }

  public void notifyObserversOfUnitEnd(ParserRuleContext ctx) {
    ASTWalkerEvent event = new ASTWalkerEvent(
        ASTWalkerEvent.eventID.END_OF_UNIT);
    event.ctx = ctx;
    event.filename = filename;
    notifyObservers(event);
  }

  public void notifyObserversOfItem(AstNode aItem) {
    ASTWalkerEvent event = new ASTWalkerEvent(
        ASTWalkerEvent.eventID.PROCESS_ITEM);
    event.item = aItem;
    event.itemStack = builderStack;
    notifyObservers(event);
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

  public CommonParserContext getContext() {
    return context;
  }

  public void setContext(CommonParserContext context) {
    this.context = context;
  }

}