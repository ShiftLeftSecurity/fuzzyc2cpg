/*
    Rule 5.2 (required):
    Identifiers in an inner scope shall not use the same name as an
    identifier in an outer scope, and therefore hide that identifier.
*/
int16_t i;
{
    int16_t i;    /* This is a different variable                       */
                  /* This is not compliant                              */
    i = 3;        /* It could be confusing as to which i this refers    */
}
