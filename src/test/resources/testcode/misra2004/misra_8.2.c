/*
    Rule 8.2 (required):
    Whenever an object or function is declared or defined, its type
    shall be explicitly stated.
*/
extern            x;            /* Non-compliant - implicit int type */
extern int16_t    x;            /* Compliant - explicit type         */
const             y;            /* Non-compliant - implicit int type */
const int16_t     y;            /* Compliant - explicit type         */
static            foo(void);    /* Non-compliant - implicit int type */
static int16_t    foo(void);    /* Compliant - explicit type         */