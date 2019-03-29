/*
    Rule 5.4 (required): A tag name shall be a unique identifier.
*/
struct stag { uint16_t a; uint16_t b; };
struct stag a1 = { 0, 0 };   /* Compliant - compatible with above */
union stag a2 = { 0, 0 };    /* Not compliant - not compatible with
                                previous declarations             */

void foo(void)
{
    struct stag { uint16_t a; }; /* Not compliant - tag stag redefined */
}

