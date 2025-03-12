grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
BOOLEAN: 'boolean';
PUBLIC : 'public' ;
RETURN : 'return' ;
STATIC: 'static';
VOID: 'void';
MAIN: 'main';
IMPORT: 'import';
NEW: 'new';
IF: 'if';
ELSE: 'else';
WHILE: 'while';
THIS: 'this';
TRUE: 'true';
FALSE: 'false';

AND: '&&';
LESS: '<';
PLUS: '+';
MINUS: '-';
MULT: '*';
DIV: '/';
EQUALS: '=';
NOT: '!';

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z0-9_]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT ID ('.' ID)* ';'
    ;

classDecl
    : CLASS name=ID ('extends' ID)? '{' methodDecl* '}'
    ;

varDecl
    : type name=ID ';'
    ;

type
    : INT
    | BOOLEAN
    ;

methodDecl
    : (PUBLIC)? type name=ID '(' (paramList)? ')' '{' varDecl* stmt* 'return' expr ';' '}'
    | (PUBLIC)? STATIC VOID MAIN '(' 'String' '[' ']' ID ')' '{' varDecl* stmt* '}'
    ;

paramList
    : param (',' param)*
    ;

param
    : type name=ID
    ;

stmt
    : expr '=' expr ';'  #AssignStmt
    | RETURN expr ';'  #ReturnStmt
    | IF '(' expr ')' stmt (ELSE stmt)? #IfStmt
    | WHILE '(' expr ')' stmt #WhileStmt
    | '{' stmt* '}' #BlockStmt
    | expr ';' #ExprStmt
    ;

expr
    : '!' expr #NotExpr
    | expr (MULT | DIV) expr  #BinaryOp
    | expr (PLUS | MINUS) expr #BinaryOp
    | expr (AND | LESS) expr  #BooleanOp
    | value=INTEGER #IntegerLiteral
    | value=TRUE #BooleanLiteral
    | value=FALSE #BooleanLiteral
    | THIS #ThisExpr
    | '(' expr ')' #ParenExpr
    | name=ID #VarRefExpr
    | expr '.' ID '(' (expr (',' expr)*)? ')' #MethodCall
    | name=ID '(' (expr (',' expr)*)? ')' #MethodCall
    ;
