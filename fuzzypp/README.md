Fuzzy PP
===

FuzzyPP provides users with the ability to preprocess a set of input C/C++ files for use with the ShiftLeft 
`fuzzyc2cpg` system.

Compiling
---

Requirements
-----
- [CMake](https://cmake.org/) (>= 3.10)

For Unix builds:
- [GNU Make](https://www.gnu.org/software/make/)
- GCC with support for C++17.

For Windows builds:
- Visual Studio 2017
- MSVC

Building
-----
From the `fuzzypp` directory, simply run:
```
cmake --build .
```

This will produce three statically-linked artifacts:
```
/lib/libfuzzyppcli-dev(.a|.lib)
/bin/fuzzyppcli(.exe)
/bin/fuzzyppcli-tests(.exe)
```

Running
---
After building the application, run `./fuzzyppcli(.exe)`.

Options
-----
Pass `--help` to the compiled binary to see a complete list of available options.

```
Usage:
  ./bin/fuzzyppcli [OPTION...]

  -f, --file FILE            A set of input source files (.c/.cpp).
  -o, --output FILE          A directory the output should be written to
  -I, DIR                    A set of paths to include on the header search
                             path
      --include FILE         A set of files to include on the header search
                             path
  -D, --define NAME[=VALUE]  A set of defined values
  -U, --undefine NAME        A set of undefined values
      --help                 Print help.
```

Example:

```
./bin/fuzzyppcli -f /home/project/source.cpp,/home/project/other_source.cpp \
                 -o /home/preprocessed_project \
                 -I /usr/includes \
                 --include /home/lib/header.hpp
                 -D DEF_NAME,VAL_NAME=1
                 -U UNDEF_NAME           
```

Dependencies
---
- [cxxopts](https://github.com/jarro2783/cxxopts)
- [simplecpp](https://github.com/danmar/simplecpp)
- [Catch2](https://github.com/catchorg/Catch2)

FAQ
---
Coming soon!
