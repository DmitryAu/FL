package ru.spbau.mit.kazakov.LParser;

import org.jetbrains.annotations.NotNull;
import ru.spbau.mit.kazakov.Lexer.Lexeme.ExtendedLexemePosition;
import ru.spbau.mit.kazakov.Lexer.Lexeme.LexemePosition;
import ru.spbau.mit.kazakov.TreePrinter;
import ru.spbau.mit.kazakov.Lexer.LexerUtils.Lexeme;
import ru.spbau.mit.kazakov.LParser.ArithmeticUtils.OperatorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ru.spbau.mit.kazakov.LParser.ArithmeticUtils.isOperator;
import static ru.spbau.mit.kazakov.LParser.ArithmeticUtils.lexemeToOperator;
import static ru.spbau.mit.kazakov.LParser.ErrorRecoveryUtils.Nonterminal;

/**
 * Grammar:
 * S          -> FunDefs Stmts | Stmts
 * FunDefs    -> FunDef FunDefs | FunDef
 * FunDef     -> def Ident Params Block
 * Params     -> (Ident, Params) | (Ident) | ()
 * Stmts      -> Stmt Stmts | Stmt
 * Stmt       -> If_stmt | While_stmt | Expr; | read (Ident); | write (Expr);
 * If_stmt    -> if (Expr) then Block else Block | if (Expr) then Block
 * While_stmt -> while (Expr) Block
 * Block      -> {Stmts}
 * Expr       -> Call | Assign | Clause
 * Call       -> Ident . Call | Ident Args
 * Args       -> (Expr, Args) | (Expr) | ()
 * Assign     -> Ident [:=, +=, -=, /=, *=, %=] Expr
 * Literal    -> true | false | Num
 * Clause     -> Conj || Clause | Conj
 * Conj       -> Conj && Eq | Eq
 * Eq         -> Eq [==, !=] Neq | Neq
 * Neq        -> Neq [<, ,<=, >, >= ] Arithm | Arithm
 * Arithm     -> Aritm [+, -] Term | Term
 * Term       -> Term [*, /, %] Power | Power
 * Power      -> Atom ^ Power | Atom
 * Atom       -> (Expr) | Literal | [++, --]Ident | Ident
 */
public class Parser {
    private List<LexemePosition> lexemes;
    private List<String> errors = new ArrayList<>();
    private int currentPosition = 0;

    public Parser(@NotNull List<LexemePosition> lexemePositions) {
        lexemes = lexemePositions;
    }

    @NotNull
    private LexemePosition currentToken() {
        if (currentPosition >= lexemes.size()) {
            return lexemes.get(lexemes.size() - 1);
        } else {
            return lexemes.get(currentPosition);
        }
    }

    private boolean parseLexeme(Lexeme lexeme) {
        return currentToken().getLexeme().equals(lexeme);
    }

    private boolean assertParseLexeme(Lexeme lexeme) {
        if (!parseLexeme(lexeme)) {
            return true;
        }
        currentPosition++;
        return false;
    }

    @NotNull
    private String stringCurrentPosition() {
        return "Line " + currentToken().getLine() + ", position " + currentToken().getBegin() + ": ";
    }

    public Node getDerivationTree() {
        Node root = new Node("root");
        root.addChild(parseDefinitions());
        root.addChild(parseStatements());

        if (errors.size() == 0) {
            return root;
        } else {
            return null;
        }
    }

    @NotNull
    public List<String> getErrors() {
        return errors;
    }

    private void skip(@NotNull Set<Lexeme> follow) {
        while (currentPosition < lexemes.size() && !follow.contains(currentToken().getLexeme())) {
            currentPosition++;
        }
    }

    private FakeNode handleError(@NotNull Nonterminal current, @NotNull Lexeme expected) {
        errors.add(stringCurrentPosition() + expected.toString()  +" expected.");
        skip(ErrorRecoveryUtils.getFollow(current));
        return new FakeNode(expected.toString());
    }

    @NotNull
    private Node parseDefinitions() {
        Node result = new Node("Definitions");

        while (parseLexeme(Lexeme.DEF)) {
            result.addChild(parseDefStatement());
        }

        return result;
    }

    @NotNull
    private Node parseDefStatement() {
        LexemePosition def = currentToken();
        if (assertParseLexeme(Lexeme.DEF)) {
            return handleError(Nonterminal.FUN_DEF, Lexeme.DEF);
        }
        LexemePosition ident = currentToken();
        if (assertParseLexeme(Lexeme.IDENTIFIER)) {
            return handleError(Nonterminal.FUN_DEF, Lexeme.IDENTIFIER);
        }
        Node result = new Node(def.toString() + ':' + ident.toString());
        result.addChild(parseParameters());
        result.addChild(parseBlock());
        return result;
    }

    @NotNull
    private Node parseBlock() {
        if (assertParseLexeme(Lexeme.LEFT_BRACE)) {
            return handleError(Nonterminal.BLOCK, Lexeme.LEFT_BRACE);
        }
        Node result = new Node("Statements");

        while (!parseLexeme(Lexeme.RIGHT_BRACE)) {
            result.addChild(parseStatement());
        }
        currentPosition++;

        return result;
    }

    @NotNull
    private Node parseStatements() {
        Node result = new Node("Statements");

        while (!currentToken().getLexeme().equals(Lexeme.EOF)) {
            Node statement = parseStatement();
            result.addChild(statement);
        }

        return result;
    }

    @NotNull
    private Node parseStatement() {
        if (parseLexeme(Lexeme.IF)) {
            return parseIfStatement();
        } else if (parseLexeme(Lexeme.WHILE)) {
            return parseWhileStatement();
        } else if (parseLexeme(Lexeme.READ)) {
            Node result = new Node(currentToken().toString());
            currentPosition++;
            if (assertParseLexeme(Lexeme.LEFT_BRACKET)) {
                return handleError(Nonterminal.STMT, Lexeme.LEFT_BRACKET);
            }
            result.addChild(new Node(currentToken().toString()));
            if (assertParseLexeme(Lexeme.IDENTIFIER)) {
                return handleError(Nonterminal.STMT, Lexeme.IDENTIFIER);
            }
            if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
                return handleError(Nonterminal.STMT, Lexeme.RIGHT_BRACKET);
            }
            if (assertParseLexeme(Lexeme.SEMICOLON)) {
                return handleError(Nonterminal.STMT, Lexeme.SEMICOLON);
            }
            return result;
        } else if (parseLexeme(Lexeme.WRITE)) {
            Node result = new Node(currentToken().toString());
            currentPosition++;
            if (assertParseLexeme(Lexeme.LEFT_BRACKET)) {
                return handleError(Nonterminal.STMT, Lexeme.LEFT_BRACKET);
            }
            result.addChild(parseExpression());
            if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
                return handleError(Nonterminal.STMT, Lexeme.RIGHT_BRACKET);
            }
            if (assertParseLexeme(Lexeme.SEMICOLON)) {
                return handleError(Nonterminal.STMT, Lexeme.SEMICOLON);
            }
            return result;
        } else {
            Node expression = parseExpression();
            if (assertParseLexeme(Lexeme.SEMICOLON)) {
                return handleError(Nonterminal.STMT, Lexeme.SEMICOLON);
            }
            return expression;
        }
    }

    private Node parseWhileStatement() {
        Node result = new Node(currentToken().toString());

        if (assertParseLexeme(Lexeme.WHILE)) {
            return handleError(Nonterminal.WHILE_STMT, Lexeme.WHILE);
        }
        if (assertParseLexeme(Lexeme.LEFT_BRACKET)) {
            return handleError(Nonterminal.WHILE_STMT, Lexeme.LEFT_BRACKET);
        }
        result.addChild(parseExpression());
        if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
            return handleError(Nonterminal.WHILE_STMT, Lexeme.RIGHT_BRACKET);
        }
        result.addChild(parseBlock());


        return result;
    }

    @NotNull
    private Node parseParameters() {
        if (assertParseLexeme(Lexeme.LEFT_BRACKET)) {
            return handleError(Nonterminal.PARAMS, Lexeme.LEFT_BRACKET);
        }
        Node result = new Node("Parameters");

        if (parseLexeme(Lexeme.IDENTIFIER)) {
            result.addChild(new Node(currentToken().toString()));
            currentPosition++;
        }
        while (parseLexeme(Lexeme.COMMA)) {
            currentPosition++;
            result.addChild(new Node(currentToken().toString()));
            if (assertParseLexeme(Lexeme.IDENTIFIER)) {
                return handleError(Nonterminal.PARAMS, Lexeme.IDENTIFIER);
            }
        }
        if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
            return handleError(Nonterminal.PARAMS, Lexeme.RIGHT_BRACKET);
        }

        return result;
    }

    @NotNull
    private Node parseIfStatement() {
        Node result = new Node(currentToken().toString());

        if (assertParseLexeme(Lexeme.IF)) {
            return handleError(Nonterminal.IF_STMT, Lexeme.IF);
        }
        if (assertParseLexeme(Lexeme.LEFT_BRACKET)) {
            return handleError(Nonterminal.IF_STMT, Lexeme.LEFT_BRACKET);
        }
        result.addChild(parseExpression());
        if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
            return handleError(Nonterminal.IF_STMT, Lexeme.RIGHT_BRACKET);
        }
        if (assertParseLexeme(Lexeme.THEN)) {
            return handleError(Nonterminal.IF_STMT, Lexeme.THEN);
        }
        result.addChild(parseBlock());
        if (parseLexeme(Lexeme.ELSE)) {
            currentPosition++;
            result.addChild(parseBlock());
        }

        return result;
    }

    @NotNull
    private Node parseExpression() {
        if (parseLexeme(Lexeme.IDENTIFIER)) {
            LexemePosition identifier = currentToken();
            currentPosition++;
            if (parseLexeme(Lexeme.ASSIGN)) {
                Node result = new Node(currentToken().toString());
                currentPosition++;
                result.addChild(new Node(identifier.toString()));
                result.addChild(parseExpression());
                return result;
            } else if(parseLexeme(Lexeme.ADD_ASSIGN)) {
                Node result = new Node(currentToken().toString());
                currentPosition++;
                result.addChild(new Node(identifier.toString()));
                Node syntacticSugar = new Node(new LexemePosition(Lexeme.ADDITION).toString());
                syntacticSugar.addChild(new Node(identifier.toString()));
                syntacticSugar.addChild(parseExpression());
                result.addChild(syntacticSugar);
                return result;
            } else if(parseLexeme(Lexeme.SUB_ASSIGN)) {
                Node result = new Node(currentToken().toString());
                currentPosition++;
                result.addChild(new Node(identifier.toString()));
                Node syntacticSugar = new Node(new LexemePosition(Lexeme.SUBTRACTION).toString());
                syntacticSugar.addChild(new Node(identifier.toString()));
                syntacticSugar.addChild(parseExpression());
                result.addChild(syntacticSugar);
                return result;
            } else if(parseLexeme(Lexeme.MULT_ASSIGN)) {
                Node result = new Node(currentToken().toString());
                currentPosition++;
                result.addChild(new Node(identifier.toString()));
                Node syntacticSugar = new Node(new LexemePosition(Lexeme.MULTIPLICATION).toString());
                syntacticSugar.addChild(new Node(identifier.toString()));
                syntacticSugar.addChild(parseExpression());
                result.addChild(syntacticSugar);
                return result;
            } else if(parseLexeme(Lexeme.DIV_ASSIGN)) {
                Node result = new Node(currentToken().toString());
                currentPosition++;
                result.addChild(new Node(identifier.toString()));
                Node syntacticSugar = new Node(new LexemePosition(Lexeme.DIVISION).toString());
                syntacticSugar.addChild(new Node(identifier.toString()));
                syntacticSugar.addChild(parseExpression());
                result.addChild(syntacticSugar);
                return result;
            } else if(parseLexeme(Lexeme.MOD_ASSIGN)) {
                Node result = new Node(currentToken().toString());
                currentPosition++;
                result.addChild(new Node(identifier.toString()));
                Node syntacticSugar = new Node(new LexemePosition(Lexeme.MODULO).toString());
                syntacticSugar.addChild(new Node(identifier.toString()));
                syntacticSugar.addChild(parseExpression());
                result.addChild(syntacticSugar);
                return result;
            } else if (parseLexeme(Lexeme.LEFT_BRACKET) || parseLexeme(Lexeme.COMPOSITION)) {
                currentPosition--;
                return parseFunCall();
            } else {
                currentPosition--;
                return parsePrecedence(OperatorType.CLAUSE);
            }
        } else {
            return parsePrecedence(OperatorType.CLAUSE);
        }
    }

    private Node parseFunCall() {
        Node result = new Node("Call:" + currentToken().toString());
        if (assertParseLexeme(Lexeme.IDENTIFIER)){
            return handleError(Nonterminal.CALL, Lexeme.IDENTIFIER);
        }

        if (parseLexeme(Lexeme.COMPOSITION)) {
            Node arguments = new Node("Arguments");
            currentPosition++;
            arguments.addChild(parseFunCall());
            result.addChild(arguments);
        } else if (parseLexeme(Lexeme.LEFT_BRACKET)) {
            result.addChild(parseArguments());
        }

        return result;
    }

    @NotNull
    private Node parseArguments() {
        if (assertParseLexeme(Lexeme.LEFT_BRACKET)) {
            return handleError(Nonterminal.ARGS, Lexeme.LEFT_BRACKET);
        }
        Node result = new Node("Arguments");
        if (!parseLexeme(Lexeme.RIGHT_BRACKET)) {
            result.addChild(parseExpression());
        }

        while (parseLexeme(Lexeme.COMMA)) {
            currentPosition++;
            result.addChild(parseExpression());
        }
        if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
            return handleError(Nonterminal.ARGS, Lexeme.RIGHT_BRACKET);
        }

        return result;
    }

    @NotNull
    private Node parsePrecedence(OperatorType precedence) {
        OperatorType lower = precedence.lower();
        Node result = lower == OperatorType.POWER ? parsePower() : parsePrecedence(lower);

        while (isOperator(currentToken().getLexeme()) && lexemeToOperator(currentToken().getLexeme()).getType().equals(precedence)) {
            Node current = new Node(currentToken().toString());
            current.addChild(result);
            currentPosition++;
            current.addChild(lower == OperatorType.POWER ? parsePower() : parsePrecedence(lower));
            result = current;
        }

        return result;
    }

    @NotNull
    private Node parsePower() {
        Node result = parseAtom();

        if (isOperator(currentToken().getLexeme()) && lexemeToOperator(currentToken().getLexeme()).getType().equals(OperatorType.POWER)) {
            Node current = new Node(currentToken().toString());
            current.addChild(result);
            currentPosition++;
            current.addChild(parsePower());
            result = current;
        }

        return result;
    }

    @NotNull
    private Node parseAtom() {
        if (parseLexeme(Lexeme.LEFT_BRACKET)) {
            currentPosition++;
            Node result = parseExpression();
            if (assertParseLexeme(Lexeme.RIGHT_BRACKET)) {
                return handleError(Nonterminal.ATOM, Lexeme.RIGHT_BRACKET);
            }
            return result;
        } else if (parseLexeme(Lexeme.IDENTIFIER)) {
            LexemePosition identifier = currentToken();
            currentPosition++;
            if (parseLexeme(Lexeme.LEFT_BRACKET)) {
                Node result = new Node("Call:" + identifier.toString());
                result.addChild(parseArguments());
                return result;
            }
            currentPosition--;
        } else if (parseLexeme(Lexeme.INCREMENT)) {
            currentPosition++;
            if(!parseLexeme(Lexeme.IDENTIFIER)) {
                return handleError(Nonterminal.ATOM, Lexeme.IDENTIFIER);
            }
            Node result = new Node(new LexemePosition(Lexeme.ASSIGN).toString());
            result.addChild(new Node(currentToken().toString()));
            Node syntacticSugar = new Node(new LexemePosition(Lexeme.ADDITION).toString());
            syntacticSugar.addChild(new Node(currentToken().toString()));
            syntacticSugar.addChild(new Node(new ExtendedLexemePosition<>(Lexeme.NUM, 1).toString()));
            result.addChild(syntacticSugar);
            currentPosition++;
            return result;
        } else if (parseLexeme(Lexeme.DECREMENT)) {
            currentPosition++;
            if(!parseLexeme(Lexeme.IDENTIFIER)) {
                return handleError(Nonterminal.ATOM, Lexeme.IDENTIFIER);
            }
            Node result = new Node(new LexemePosition(Lexeme.ASSIGN).toString());
            result.addChild(new Node(currentToken().toString()));
            Node syntacticSugar = new Node(new LexemePosition(Lexeme.SUBTRACTION).toString());
            syntacticSugar.addChild(new Node(currentToken().toString()));
            syntacticSugar.addChild(new Node(new ExtendedLexemePosition<>(Lexeme.NUM, 1).toString()));
            result.addChild(syntacticSugar);
            currentPosition++;
            return result;
        }
        if (parseLexeme(Lexeme.NUM) || parseLexeme(Lexeme.IDENTIFIER) || parseLexeme(Lexeme.BOOLEAN)) {
            Node result = new Node(currentToken().toString());
            currentPosition++;
            return result;
        } else {
            return handleError(Nonterminal.ATOM, Lexeme.IDENTIFIER);
        }
    }

    private static class Node implements TreePrinter.PrintableNode {
        protected String text;
        private List<TreePrinter.PrintableNode> children = new ArrayList<>();

        public Node(@NotNull String text) {
            this.text = text;
        }

        public void addChild(@NotNull Node child) {
            children.add(child);
        }

        @Override
        @NotNull
        public List<TreePrinter.PrintableNode> getChildren() {
            return children;
        }

        @Override
        @NotNull
        public String getText() {
            return text;
        }
    }

    private static class FakeNode extends Node {
        public FakeNode(@NotNull String text) {
            super(text);
        }

        @Override
        @NotNull
        public String getText() {
            return "Fake: " + text;
        }
    }
}
