/*
 * Copyright © 2017-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Distributed under the License on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

grammar Directives;

options {
  language = Java;
}

@lexer::header {
  // Same license as above for the generated lexer code
}

// Entry point of the grammar
recipe
 : statements EOF
 ;

// Set of statements (can include directives, macros, pragmas, comments, or if-statements)
statements
 : ( Comment | macro | directive ';' | pragma ';' | ifStatement )*
 ;

// A directive represents a command and its arguments (optional)
directive
 : command
   (
     codeblock | identifier | macro | text | number | bool | column
     | colList | numberList | boolList | stringList
     | numberRanges | properties | byteSizeArg | timeDurationArg
   )*?
;


// Conditional if-else directive block
ifStatement
 : ifStat elseIfStat* elseStat? '}'
 ;

// IF block
ifStat
 : 'if' expression '{' statements
 ;

// ELSE IF block
elseIfStat
 : '}' 'else' 'if' expression '{' statements
 ;

// ELSE block
elseStat
 : '}' 'else' '{' statements
 ;

// Recursive expression within parentheses
expression
 : '(' (~'(' | expression)* ')'
 ;

// FOR loop statement
forStatement
 : 'for' '(' Identifier '=' expression ';' expression ';' expression ')' '{' statements '}'
 ;

// Macro definition using ${...}
macro
 : Dollar OBrace (~OBrace | macro | Macro)*? CBrace
 ;

// Preprocessing pragma
pragma
 : '#pragma' (pragmaLoadDirective | pragmaVersion)
 ;

// Load directives via pragma
pragmaLoadDirective
 : 'load-directives' identifierList
 ;

// Specify version in pragma
pragmaVersion
 : 'version' Number
 ;

// Code block for expression evaluation
codeblock
 : 'exp' Space* ':' condition
 ;

// Identifier token rule
identifier
 : Identifier
 ;

// Directive-specific properties
properties
 : 'prop' ':' OBrace (propertyList)+ CBrace
 | 'prop' ':' OBrace OBrace (propertyList)+ CBrace { notifyErrorListeners("Too many start parentheses"); }
 | 'prop' ':' OBrace (propertyList)+ CBrace CBrace { notifyErrorListeners("Too many end parentheses"); }
 | 'prop' ':' (propertyList)+ CBrace { notifyErrorListeners("Missing opening brace"); }
 | 'prop' ':' OBrace (propertyList)+ { notifyErrorListeners("Missing closing brace"); }
 ;

// List of properties inside a property block
propertyList
 : property (',' property)*
 ;

// A property is an identifier and value pair
property
 : Identifier '=' ( text | number | bool )
 ;

// List of number ranges
numberRanges
 : numberRange ( ',' numberRange)*
 ;

// Format for specifying a single number range
numberRange
 : Number ':' Number '=' value
 ;

// Allowed values in numberRange
value
 : String
 | Number
 | Column
 | Bool
 | BYTE_SIZE
 | TIME_DURATION   // This allows BYTE_SIZE and TIME_DURATION to be used in numberRange
 ;

// External command syntax using '!'
ecommand
 : '!' Identifier
 ;

// A configuration token (not used above)
config
 : Identifier
 ;

// Column name
column
 : Column
 ;

// Text literal
text
 : String
 ;

// Number literal
number
 : Number
 ;

// Byte size argument (e.g., 10KB, 100MB, 1GB)
byteSizeArg
 : BYTE_SIZE
 ;

// Time duration argument (e.g., 5s, 2m, 1h)
timeDurationArg
 : TIME_DURATION
 ;

// Byte size literal (e.g., 10KB, 5MB)
BYTE_SIZE
 : Int BYTE_UNIT
 ;

// Time duration literal (e.g., 10ms, 5h)
TIME_DURATION
 : Int TIME_UNIT
 ;

// Valid byte units (KB, MB, etc.)
fragment BYTE_UNIT
 : [KkMmGgTt][Bb]
 ;

// Valid time units (ms, s, m, h, d)
fragment TIME_UNIT
 : 'ms' | 's' | 'm' | 'h' | 'd'
 ;

// Boolean literal
bool
 : Bool
 ;

// A nested condition expression (used in codeblock)
condition
 : OBrace (~CBrace | condition)* CBrace
 ;

// Command name (e.g., 'drop', 'filter')
command
 : Identifier
 ;

// List of columns (e.g., :col1, :col2)
colList
 : Column (',' Column)+
 ;

// List of numbers
numberList
 : Number (',' Number)+
 ;

// List of booleans
boolList
 : Bool (',' Bool)+
 ;

// List of strings
stringList
 : String (',' String)+
 ;

// List of identifiers
identifierList
 : Identifier (',' Identifier)*
 ;

// Lexer rules start here
OBrace   : '{';
CBrace   : '}';
SColon   : ';';
Or       : '||';
And      : '&&';
Equals   : '==';
NEquals  : '!=';
GTEquals : '>=';
LTEquals : '<=';
Match    : '=~';
NotMatch : '!~';
QuestionColon : '?:';
StartsWith : '=^';
NotStartsWith : '!^';
EndsWith : '=$';
NotEndsWith : '!$';
PlusEqual : '+=';     // Compound assignment operators
SubEqual : '-=';
MulEqual : '*=';
DivEqual : '/=';
PerEqual : '%=';
AndEqual : '&=';
OrEqual  : '|=';
XOREqual : '^=';
Pow      : '^';       // Power operator
External : '!';       // For ecommand
GT       : '>';
LT       : '<';
Add      : '+';
Subtract : '-';
Multiply : '*';
Divide   : '/';
Modulus  : '%';
OBracket : '[';
CBracket : ']';
OParen   : '(';
CParen   : ')';
Assign   : '=';
Comma    : ',';
QMark    : '?';
Colon    : ':';
Dot      : '.';
At       : '@';
Pipe     : '|';
BackSlash: '\\';
Dollar   : '$';
Tilde    : '~';

// Boolean keywords
Bool
 : 'true'
 | 'false'
 ;

// Number literals
Number
 : Int ('.' Digit*)?
 ;

// Valid identifiers for variables, directives
Identifier
 : [a-zA-Z_\-] [a-zA-Z_0-9\-]*
 ;

// Macro names inside ${...}
Macro
 : [a-zA-Z_] [a-zA-Z_0-9]*
 ;

// Column identifiers (e.g., :columnName)
Column
 : ':' [a-zA-Z_\-] [:a-zA-Z_0-9\-]*
 ;

// String literals (single or double quoted)
String
 : '\'' ( EscapeSequence | ~('\'') )* '\''
 | '"'  ( EscapeSequence | ~('"') )* '"'
 ;

// String escape sequences
EscapeSequence
 : '\\' ('b'|'t'|'n'|'f'|'r'|'"'|'\''|'\\')
 | UnicodeEscape
 | OctalEscape
 ;

// Octal character escape
fragment OctalEscape
 : '\\' ('0'..'3') ('0'..'7') ('0'..'7')
 | '\\' ('0'..'7') ('0'..'7')
 | '\\' ('0'..'7')
 ;

// Unicode character escape
fragment UnicodeEscape
 : '\\' 'u' HexDigit HexDigit HexDigit HexDigit
 ;

// Valid hexadecimal digit
fragment HexDigit
 : ('0'..'9'|'a'..'f'|'A'..'F')
 ;

// Skipped comment tokens (line and block)
Comment
 : ('//' ~[\r\n]* | '/*' .*? '*/' | '--' ~[\r\n]* ) -> skip
 ;

// Skipped whitespace
Space
 : [ \t\r\n\u000C]+ -> skip
 ;

// Integer literals
fragment Int
 : '-'? [1-9] Digit* [L]*   // Optional 'L' for long numbers
 | '0'
 ;

// Digit (0-9)
fragment Digit
 : [0-9]
 ;
