package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {
  }

  private final List<Token> tokens;
  private int current = 0;

  // Takes in a list of tokens.
  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  // Return a list of statements.
  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }
    return statements;
  }

  /*
   * ------------------------------------------------------------
   * | Grammer Notation | Code Representation
   * ------------------------------------------------------------
   * | Terminal | Code to match and consume a token.
   * | Nonterminal | Call to that rule's function.
   * | '|' | 'if' or 'switch' statement.
   * | '*' or '+' | 'while' or 'for' loop.
   * | '?' | 'if' statement.
   * ------------------------------------------------------------
   */

  private Expr expression() {
    // expression → assignment;
    return assignment();
  }

  private Stmt declaration() {
    // declaration → classDecl | funDecl | varDecl | statement ;
    try {
      if (match(CLASS))
        return classDeclaration();
      if (match(FUN))
        return function("function");
      if (match(VAR))
        return varDeclaration();
      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    // classDecl → "class" IDENTIFIER ( "<" IDENTIFIER )? "{" function* "}" ;
    Token name = consume(IDENTIFIER, "Expect class name.");
    Expr.Variable superclass = null;
    if (match(LESS)) {
      // class sub < super {}
      consume(IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(previous());
    }
    consume(LEFT_BRACE, "Expect '{' before class body.");
    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }
    consume(RIGHT_BRACE, "Expect '}' after class body.");
    return new Stmt.Class(name, superclass, methods);
  }

  private Stmt statement() {
    // statement → exprStmt | forStmt | ifStmt | printStmt | returnStmt | whileStmt
    // | block ;
    if (match(FOR))
      return forStatement();
    if (match(IF))
      return ifStatement();
    if (match(PRINT))
      return printStatement();
    if (match(RETURN))
      return returnStatement();
    if (match(WHILE))
      return whileStatement();
    if (match(LEFT_BRACE))
      return new Stmt.Block(block());
    return expressionStatement();
  }

  private Stmt forStatement() {
    // forStmt → "for" "(" ( varDecl | exprStmt | ";" expression? ";"
    consume(LEFT_PAREN, "Expect '(' after 'for'.");
    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body = statement();
    if (increment != null) {
      body = new Stmt.Block(
          Arrays.asList(
              body,
              new Stmt.Expression(increment)));
    }
    if (condition == null) // Infinity.
      condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);
    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }
    return body;
  }

  private Stmt ifStatement() {
    // ifStmt → "if" "(" expression ")" statement ( "else" statement )? ;
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition.");
    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }
    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt printStatement() {
    // printStmt → "print" expression ";" ;
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt returnStatement() {
    // returnStmt → "return" expression? ";" ;
    Token keyword = previous();
    Expr value = null;
    // In case of: return expression;
    if (!check(SEMICOLON)) {
      value = expression();
    }
    // In case of: return;
    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt varDeclaration() {
    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    Token name = consume(IDENTIFIER, "Expect variable name.");
    Expr initializer = null;
    // In case of: var x = value;
    if (match(EQUAL)) {
      initializer = expression();
    }
    // In case of: var x;
    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt whileStatement() {
    // whileStmt → "while" "(" expression ")" statement ;
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after condition.");
    Stmt body = statement();
    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    // expression;
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Stmt.Function function(String kind) {
    // function → IDENTIFIER "(" parameters? ")" block ;
    // parameters → IDENTIFIER ( "," IDENTIFIER )*;
    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
    List<Token> parameters = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }
        parameters.add(
            consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");
    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body);
  }

  private List<Stmt> block() {
    // block → "{" declaration* "}" ;
    List<Stmt> statements = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }
    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Expr assignment() {
    // assignment → ( call "." )? IDENTIFIER "=" assignment | logic_or ;
    Expr expr = or();
    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();
      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable) expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get) expr;
        return new Expr.Set(get.object, get.name, value);
      }
      error(equals, "Invalid assignment target.");
    }
    return expr;
  }

  private Expr or() {
    // logic_or → logic_and ( "or" logic_and )* ;
    Expr expr = and();
    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }
    return expr;
  }

  private Expr and() {
    // logic_and → equality ( "and" equality )* ;
    Expr expr = equality();
    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }
    return expr;
  }

  private Expr equality() {
    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    Expr expr = comparison();
    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr comparison() {
    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    Expr expr = term();
    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr term() {
    // term → factor ( ( "-" | "+" ) factor)*;
    Expr expr = factor();
    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr factor() {
    // factor → unary ( ( "/" | "*" ) unary )* ;
    Expr expr = unary();
    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private Expr unary() {
    // unary → ( "!" | "-" ) unary | call ;
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return call();
  }

  private Expr finishCall(Expr callee) {
    // arguments → expression ( "," expression )* ;
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }
        arguments.add(expression());
      } while (match(COMMA));
    }
    Token paren = consume(RIGHT_PAREN,
        "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, arguments);
  }

  private Expr call() {
    // call → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
    Expr expr = primary();
    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(DOT)) {
        Token name = consume(IDENTIFIER,
            "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }
    return expr;
  }

  private Expr primary() {
    // primary → "true" | "false" | "nil" | "this" | NUMBER | STRING | IDENTIFIER |
    // "(" expression ")" | "super" "." IDENTIFIER
    if (match(FALSE))
      return new Expr.Literal(false);
    if (match(TRUE))
      return new Expr.Literal(true);
    if (match(NIL))
      return new Expr.Literal(null);
    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }
    if (match(SUPER)) {
      Token keyword = previous();
      consume(DOT, "Expect '.' after 'super'.");
      Token method = consume(IDENTIFIER,
          "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }
    if (match(THIS))
      return new Expr.This(previous());
    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }
    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }
    throw error(peek(), "Expect expression.");
  }

  // Check if the current token type match any type of the given.
  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  // If the current token has the same token type of the passed type, it advances
  // and returns the token.
  // Else, it returns an error with the specified message.
  private Token consume(TokenType type, String message) {
    if (check(type))
      return advance();
    throw error(peek(), message);
  }

  // Check if the current token has the same type of the specified type.
  private boolean check(TokenType type) {
    if (isAtEnd())
      return false;
    return peek().type == type;
  }

  // Return the current token and advance the current pointer.
  private Token advance() {
    if (!isAtEnd())
      current++;
    return previous();
  }

  // Return true if we reached the end of the list of tokens.
  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  // Return current token.
  private Token peek() {
    return tokens.get(current);
  }

  // Return previous token.
  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON)
        return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}