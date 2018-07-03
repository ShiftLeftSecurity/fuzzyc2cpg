grammar Module;

import ModuleLex, Common;

/*
    Copyright (C) 2013 Fabian 'fabs' Yamaguchi <fabs@phenoelit.de>
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

code : (function_def | simple_decl | using_directive | water)*;

using_directive: USING NAMESPACE identifier ';';

function_def : template_decl_start? return_type? function_name
            function_param_list ctor_list? compound_statement;

return_type : (function_decl_specifiers* type_name) ptr_operator*;

function_param_list : '(' parameter_decl_clause? ')' CV_QUALIFIER* exception_specification?;

parameter_decl_clause: (parameter_decl (',' parameter_decl)*) (',' '...')?
                     | VOID;
parameter_decl : param_decl_specifiers parameter_id;
parameter_id: ptrs? ('(' parameter_id ')' | parameter_name) type_suffix?;

compound_statement: OPENING_CURLY { skipToEndOfObject(); };

ctor_list: ':'  ctor_initializer (',' ctor_initializer)*;
ctor_initializer:  initializer_id ctor_expr;
initializer_id : '::'? identifier;
ctor_expr:  '(' expr? ')';

function_name: '(' function_name ')' | identifier | OPERATOR operator;

exception_specification : THROW '(' type_id_list ')';
type_id_list: no_brackets* ('(' type_id_list ')' no_brackets*)*;



// The following two contain 'water'-rules for expressions

init_declarator : declarator (('(' expr? ')') | ('=' assign_expr_w_))?;
declarator: ptrs? identifier type_suffix? |
            ptrs? '(' func_ptrs identifier ')' type_suffix;

type_suffix : ('[' constant_expr_w_ ']') | param_type_list;

// water rules for expressions

assign_expr_w_: assign_water*
        (('{' assign_expr_w__l2 '}' | '(' assign_expr_w__l2 ')' | '[' assign_expr_w__l2 ']')
             assign_water*)*;

assign_expr_w__l2: assign_water_l2* (('{' assign_expr_w__l2 '}' | '(' assign_expr_w__l2 ')' | '[' assign_expr_w__l2 ']')
             assign_water_l2*)*;

constant_expr_w_: no_squares* ('[' constant_expr_w_ ']' no_squares*)*;

simple_decl : (TYPEDEF? template_decl_start?) var_decl;

var_decl : class_def init_declarator_list? #declByClass
         | type_name init_declarator_list #declByType
         ;

init_declarator_list: init_declarator (',' init_declarator)* ';';

initializer: assign_expr
           |'{' initializer_list '}'
;

initializer_list: initializer (',' initializer)*;


class_def: CLASS_KEY class_name? base_classes? OPENING_CURLY {skipToEndOfObject(); } ;
class_name: identifier;
base_classes: ':' base_class (',' base_class)*;
base_class: VIRTUAL? access_specifier? identifier;

type_name : (CV_QUALIFIER* (CLASS_KEY | UNSIGNED | SIGNED)?
            base_type ('<' template_param_list '>')? ('::' base_type ('<' template_param_list '>')? )*) CV_QUALIFIER?
          | UNSIGNED
          | SIGNED
          ;


base_type: (ALPHA_NUMERIC | VOID | LONG | LONG)+;

// Parameters

param_decl_specifiers : (AUTO | REGISTER)? type_name;

// this is a bit misleading. We're just allowing access_specifiers
// here because C programs can use 'public', 'protected' or 'private'
// as variable names.

parameter_name: identifier;

param_type_list: '(' VOID ')'
               | '(' (param_type (',' param_type)*)? ')';

param_type: param_decl_specifiers param_type_id;
param_type_id: ptrs? ('(' param_type_id ')' | parameter_name?) type_suffix?;

// operator-identifiers not implemented
identifier : (ALPHA_NUMERIC ('::' ALPHA_NUMERIC)*) | access_specifier;
number: HEX_LITERAL | DECIMAL_LITERAL | OCTAL_LITERAL;

ptrs: (ptr_operator 'restrict'?)+;
func_ptrs: ptrs;

expr: assign_expr (',' expr)?;

assign_expr: conditional_expression (assignment_operator assign_expr)?;
conditional_expression: or_expression #normOr
		      | or_expression ('?' expr ':' conditional_expression) #cndExpr;


or_expression : and_expression ('||' or_expression)?;
and_expression : inclusive_or_expression ('&&' and_expression)?;
inclusive_or_expression: exclusive_or_expression ('|' inclusive_or_expression)?;
exclusive_or_expression: bit_and_expression ('^' exclusive_or_expression)?;
bit_and_expression: equality_expression ('&' bit_and_expression)?;
equality_expression: relational_expression (equality_operator equality_expression)?;
relational_expression: shift_expression (relational_operator relational_expression)?;
shift_expression: additive_expression ( ('<<'|'>>') shift_expression)?;
additive_expression: multiplicative_expression (('+'| '-') additive_expression)?;
multiplicative_expression: cast_expression ( ('*'| '/'| '%') multiplicative_expression)?;

cast_expression: ('(' cast_target ')' cast_expression)
               | unary_expression
;

cast_target: type_name ptr_operator*;

// currently does not implement delete

unary_expression: inc_dec cast_expression
                | unary_op_and_cast_expr
                | sizeof_expression 
                | new_expression
                | postfix_expression
                ;

new_expression: '::'? NEW type_name '[' conditional_expression? ']' 
              | '::'? NEW type_name '(' expr? ')'
              ;

unary_op_and_cast_expr: unary_operator cast_expression;

sizeof_expression: sizeof '(' sizeof_operand ')'
                 | sizeof sizeof_operand2;

sizeof: 'sizeof';

sizeof_operand: type_name ptr_operator *;
sizeof_operand2: unary_expression;

inc_dec: ('--' | '++');

// this is a bit misleading. We're just allowing access_specifiers
// here because C programs can use 'public', 'protected' or 'private'
// as variable names.

postfix_expression: postfix_expression '[' expr ']' #arrayIndexing
                  | postfix_expression '(' function_argument_list ')' #funcCall
                  | postfix_expression '.' TEMPLATE? (identifier) #memberAccess
                  | postfix_expression '->' TEMPLATE? (identifier) #ptrMemberAccess
                  | postfix_expression inc_dec #incDecOp
                  | primary_expression # primaryOnly
                  ;

function_argument_list: ( function_argument (',' function_argument)* )?;
function_argument: assign_expr;


primary_expression: identifier | constant | '(' expr ')';
