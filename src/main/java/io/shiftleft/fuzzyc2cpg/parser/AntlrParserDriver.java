package io.shiftleft.fuzzyc2cpg.parser;

import io.shiftleft.fuzzyc2cpg.Utils;
import io.shiftleft.fuzzyc2cpg.ast.AstNode;
import io.shiftleft.fuzzyc2cpg.ast.AstNodeBuilder;
import io.shiftleft.fuzzyc2cpg.ast.logical.statements.CompoundStatement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

import io.shiftleft.fuzzyc2cpg.output.CpgOutputModuleFactory;
import io.shiftleft.proto.cpg.Cpg;
import jdk.nashorn.internal.runtime.ParserException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import static org.antlr.v4.runtime.Token.EOF;

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
  private CpgOutputModuleFactory outputModuleFactory;
  private Cpg.CpgStruct.Builder cpg;
  private Cpg.CpgStruct.Node namespaceBlock;
  private Cpg.CpgStruct.Node fileNode;

  public AntlrParserDriver() {
    super();
  }

  public void setOutputModuleFactory(CpgOutputModuleFactory factory) {
    this.outputModuleFactory = factory;
  }

  public void setCpg(Cpg.CpgStruct.Builder cpg) {
    this.cpg = cpg;
  }

  public void setNamespaceBlock(Cpg.CpgStruct.Node namespaceBlock) {
    this.namespaceBlock = namespaceBlock;
  }

  public void setFileNode(Cpg.CpgStruct.Node fileNode) {
    this.fileNode = fileNode;
  }

  public abstract ParseTree parseTokenStreamImpl(TokenSubStream tokens);

  public abstract Lexer createLexer(CharStream input);

  public void parseAndWalkFile(String filename) throws ParserException {
    handleHiddenTokens(filename);
    TokenSubStream stream = createTokenStreamFromFile(filename);
    initializeContextWithFile(filename, stream);

    ParseTree tree = parseTokenStream(stream);
    walkTree(tree);
  }

  private void handleHiddenTokens(String filename) {
    CommonTokenStream tokenStream = createStreamOfHiddenTokensFromFile(filename);
    TokenSource tokenSource = tokenStream.getTokenSource();

    while (true){
      Token token = tokenSource.nextToken();
      if (token.getType() == EOF) {
        break;
      }
      if (token.getChannel() != Token.HIDDEN_CHANNEL) {
        continue;
      }
      int line = token.getLine();
      String text = token.getText();
      // We can add to `CPG` here

      Cpg.CpgStruct.Node commentNode = Utils.newNode(Cpg.CpgStruct.Node.NodeType.COMMENT)
              .addProperty(Cpg.CpgStruct.Node.Property.newBuilder()
                      .setName(Cpg.NodePropertyName.LINE_NUMBER)
                      .setValue(Cpg.PropertyValue.newBuilder().setIntValue(line)))
              .addProperty(Cpg.CpgStruct.Node.Property.newBuilder()
                      .setName(Cpg.NodePropertyName.CODE)
                      .setValue(Cpg.PropertyValue.newBuilder().setStringValue(text))
              )
              .build();

      cpg.addNode(commentNode);

      cpg.addEdge(Cpg.CpgStruct.Edge.newBuilder()
              .setType(Cpg.CpgStruct.Edge.EdgeType.AST)
              .setSrc(fileNode.getKey())
              .setDst(commentNode.getKey())
      );

    }
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
    CharStream inputStream = CharStreams.fromString(input);
    Lexer lex = createLexer(inputStream);
    TokenSubStream tokens = new TokenSubStream(lex);
    ParseTree tree = parseTokenStream(tokens);
    return tree;
  }

  protected TokenSubStream createTokenStreamFromFile(String filename)
      throws ParserException {

    CharStream input = createInputStreamForFile(filename);
    Lexer lexer = createLexer(input);
    TokenSubStream tokens = new TokenSubStream(lexer);
    return tokens;

  }

  private CharStream createInputStreamForFile(String filename) {

    try {
      return CharStreams.fromFileName(filename);
    } catch (IOException exception) {
      throw new RuntimeException(String.format("Unable to find source file [%s]", filename));
    }

  }

  protected CommonTokenStream createStreamOfHiddenTokensFromFile(String filename) {
    CharStream input = createInputStreamForFile(filename);
    Lexer lexer = createLexer(input);
    return new CommonTokenStream(lexer, Token.HIDDEN_CHANNEL);
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

  public CommonParserContext getContext() {
    return context;
  }

  public void setContext(CommonParserContext context) {
    this.context = context;
  }

}