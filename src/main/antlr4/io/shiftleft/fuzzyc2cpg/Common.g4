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

ptrs: (ptr_operator 'restrict'?)+;
func_ptrs: ptrs;



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
