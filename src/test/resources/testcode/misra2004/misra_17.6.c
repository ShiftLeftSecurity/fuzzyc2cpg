/*
    Rule 17.6 (required):
    The address of an object with automatic storage shall not be
    assigned to another object that may persist after the first object
    has ceased to exist.
*/

int8_t * foobar(void)
{
    int8_t local_auto;
    return (&local_auto); /* not compliant */
}