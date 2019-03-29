/*
    Rule 17.4 (required):
    Array indexing shall be the only allowed form of pointer
    arithmetic.
*/


void my_fn(uint8_t * p1, uint8_t p2[])
{
    uint8_t index = 0U;
    uint8_t * p3;
    uint8_t * p4;
    *p1 = 0U;
    p1 ++;                /* not compliant - pointer increment               */
    p1 = p1 + 5;          /* not compliant - pointer increment               */  
    p1[5] = 0U;           /* not compliant - p1 was not declared as an array */
    p3 = &p1[5];          /* not compliant - p1 was not declared as an array */
    p2[0] = 0U;                                                              
    index ++;                                                                
    index = index + 5U;                                                      
    p2[index] = 0U;       /* compliant                                       */
    p4 = &p2[5];          /* compliant                                       */
}

uint8_t a1[16];
uint8_t a2[16];
my_fn(a1, a2);
my_fn(&a1[4], &a2[4]);
uint8_t a[10];
uint8_t * p;
p = a;
*(p+5) = 0U;             /* not compliant */
p[5] = 0U;               /* not compliant */