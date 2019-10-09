This is a version of [simplecpp](https://github.com/danmar/simplecpp), modified to retain comments in the preprocessor output.

The change is as follows:
```
--- a/fuzzypp/external/simplecpp/simplecpp.cpp
+++ b/fuzzypp/external/simplecpp/simplecpp.cpp
@@ -2495,8 +2495,7 @@ static bool preprocessToken(simplecpp::TokenList &output, const simplecpp::Token
         }
         output.takeTokens(value);
     } else {
-        if (!tok->comment)
-            output.push_back(new simplecpp::Token(*tok));
+        output.push_back(new simplecpp::Token(*tok));
         *tok1 = tok->next;
     }
     return true;
```
