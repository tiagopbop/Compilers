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
    : (importDecl)* classDecl EOF                                                            #ProgramBeggining
    ;

importDecl
    : IMPORT name=ID ('.' name=ID)* ';'                                                      #ImportDeclaration
    ;

classDecl
    : CLASS name=ID ('extends' superclass=ID)? '{' varDecl* methodDecl* '}'                  #ClassDeclaration
    ;

varDecl
    : type name=ID ';'                                                                       #VarDeclaration
    ;

type
    : name=INT                                                                               #IntType
    | name=INT '[' ']'                                                                       #ArrayType
    | name=BOOLEAN ('[' ']')?                                                                #BooleanType
    | name=ID                                                                                #ClassType
    ;

methodDecl locals[boolean isMain=false]
    : (PUBLIC)? type method=ID '(' (param (',' param)*)? ')' '{' varDecl* stmt* '}'              #MethodDeclaration
    | (PUBLIC)? STATIC VOID {$isMain=true;} method=MAIN '(' name=ID '[' ']' name=ID ')' '{' varDecl * stmt* '}' #MainMethodDeclaration
    ;
param
    : type name=ID                                                                           #Parameter
    | type '...' name=ID                                                                     #VarArgParameter
    ;

stmt
    : expr '[' expr ']' '=' expr ';'                                                         #ArrayAssignStatement
    | expr '=' expr ';'                                                                      #AssignStatement
    | RETURN expr ';'                                                                        #ReturnStatement
    | IF '(' expr ')' stmt (ELSE stmt)?                                                      #IfStatement
    | WHILE '(' expr ')' stmt                                                                #WhileStatement
    | '{' stmt* '}'                                                                          #BlockStatement
    | expr ';'                                                                               #ExprStatement
    ;

expr
    : '[' expr (',' expr)* ']'                                                               #ArrayInitializationExpr
    | 'new' type '[' expr ']'                                                                #NewArrayExpr
    | expr '[' expr ']'                                                                      #ArrayAccess
    | 'new' name=ID '(' ')'                                                                  #NewClassExpr
    | expr '.' 'length'                                                                      #LengthExpr
    | '!' expr                                                                               #NotExpr
    | expr operation=(MULT | DIV) expr                                                       #BinaryOp
    | expr operation=(PLUS | MINUS) expr                                                     #BinaryOp
    | expr operation=(AND | LESS) expr                                                       #BooleanOp
    | value=INTEGER                                                                          #IntegerLiteral
    | value=TRUE                                                                             #BooleanLiteral
    | value=FALSE                                                                            #BooleanLiteral
    | THIS                                                                                   #ThisExpr
    | '(' expr ')'                                                                           #ParenthesisExpr
    | name=ID                                                                                #VarRefExpr
    | expr '.' name=ID '(' (expr (',' expr)*)? ')'                                           #MethodCall
    | name=ID '(' (expr (',' expr)*)? ')'                                                    #MethodCall
    ;