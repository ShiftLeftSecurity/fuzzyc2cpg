grammar Common;

import ModuleLex;

@header{
  import java.util.Stack;
}


@parser::members
{
            public boolean skipToEndOfObject()
            {
                Stack<Object> CurlyStack = new Stack<Object>();
                Object o = new Object();
                int t = _input.LA(1);

                while(t != EOF && !(CurlyStack.empty() && t == CLOSING_CURLY)){
                    
                    if(t == PRE_ELSE){
                        Stack<Object> ifdefStack = new Stack<Object>();
                        consume();
                        t = _input.LA(1);
                        
                        while(t != EOF && !(ifdefStack.empty() && (t == PRE_ENDIF))){
                            if(t == PRE_IF)
                                ifdefStack.push(o);
                            else if(t == PRE_ENDIF)
                                ifdefStack.pop();
                            consume();
                            t = _input.LA(1);
                        }
                    }
                    
                    if(t == OPENING_CURLY)
                        CurlyStack.push(o);
                    else if(t == CLOSING_CURLY)
                        CurlyStack.pop();
                    
                    consume();
                    t = _input.LA(1);
                }
                if(t != EOF)
                    consume();
                return true;
            }

   // this should go into FunctionGrammar but ANTLR fails
   // to join the parser::members-section on inclusion
   
   public boolean preProcSkipToEnd()
   {
                Stack<Object> CurlyStack = new Stack<Object>();
                Object o = new Object();
                int t = _input.LA(1);

                while(t != EOF && !(CurlyStack.empty() && t == PRE_ENDIF)){
                                        
                    if(t == PRE_IF)
                        CurlyStack.push(o);
                    else if(t == PRE_ENDIF)
                        CurlyStack.pop();
                    
                    consume();
                    t = _input.LA(1);
                }
                if(t != EOF)
                    consume();
                return true;
   }

}

unary_operator : '&' | '*' | '+'| '-' | '~' | '!';
relational_operator: ('<'|'>'|'<='|'>=');

constant
    :   HEX_LITERAL
    |   OCTAL_LITERAL
    |   DECIMAL_LITERAL
    |   BINARY_LITERAL
	|	STRING
    |   CHAR
    |   FLOATING_POINT_LITERAL
    ;

// keywords & operators

function_decl_specifiers: ('inline' | 'virtual' | 'explicit' | 'friend' | 'static');
ptr_operator: ('*' | '&');

access_specifier: ('public' | 'private' | 'protected');

operator: (('new' | 'delete' ) ('[' ']')?)
  | '+' | '-' | '*' | '/' | '%' |'^' | '&' | '|' | '~'
  | '!' | '=' | '<' | '>' | '+=' | '-=' | '*='
  | '/=' | '%=' | '^=' | '&=' | '|=' | '>>'
  |'<<'| '>>=' | '<<=' | '==' | '!='
  | '<=' | '>=' | '&&' | '||' | '++' | '--'
  | ',' | '->*' | '->' | '(' ')' | '[' ']'
  ;

assignment_operator: '=' | '*=' | '/=' | '%=' | '+=' | '-=' | '<<=' | '>>=' | '&=' | '^=' | '|='; 
equality_operator: ('=='| '!=');

template_decl_start : TEMPLATE '<' template_param_list '>';


// template water
template_param_list : (('<' template_param_list '>') |
                       ('(' template_param_list ')') | 
                       no_angle_brackets_or_brackets)+
;

// water

no_brackets: ~('(' | ')');
no_brackets_curlies_or_squares: ~('(' | ')' | '{' | '}' | '[' | ']');
no_brackets_or_semicolon: ~('(' | ')' | ';');
no_angle_brackets_or_brackets : ~('<' | '>' | '(' | ')');
no_curlies: ~('{' | '}');
no_squares: ~('[' | ']');
no_squares_or_semicolon: ~('[' | ']' | ';');
no_comma_or_semicolon: ~(',' | ';');

assign_water: ~('(' | ')' | '{' | '}' | '[' | ']' | ';' | ',');
assign_water_l2: ~('(' | ')' | '{' | '}' | '[' | ']');

water: .;



// operator-identifiers not implemented
identifier : (ALPHA_NUMERIC ('::' ALPHA_NUMERIC)*) | access_specifier;
number: HEX_LITERAL | DECIMAL_LITERAL | OCTAL_LITERAL;

ptrs: (CV_QUALIFIER? ptr_operator 'restrict'?)+;
func_ptrs: ptrs;



class_def: CLASS_KEY gcc_attribute? class_name? base_classes? OPENING_CURLY {skipToEndOfObject(); } ;
class_name: identifier;
base_classes: ':' base_class (',' base_class)*;
base_class: VIRTUAL? access_specifier? identifier;


type_name : (CV_QUALIFIER* (CLASS_KEY | UNSIGNED | SIGNED)?
            base_type ('<' template_param_list '>')? ('::' base_type ('<' template_param_list '>')? )*) CV_QUALIFIER?
          | UNSIGNED
          | SIGNED
          ;

base_type: (ALPHA_NUMERIC | VOID | LONG | LONG)+;

gcc_attribute: GCC_ATTRIBUTE '(' '(' identifier ')' ')';

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

sizeof_expression: sizeof sizeof_operand2
                 | sizeof '(' sizeof_operand2 ')'
                 | sizeof '(' sizeof_operand ')';

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
