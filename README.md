[![Build Status](https://secure.travis-ci.org/ShiftLeftSecurity/fuzzyc2cpg.png?branch=master)](http://travis-ci.org/ShiftLeftSecurity/fuzzyc2cpg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.shiftleft/fuzzyc2cpg_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.shiftleft/fuzzyc2cpg_2.12)

# fuzzyc2cpg

**Note: for first-time users, we recommend building "joern" at https://github.com/ShiftLeftSecurity/joern/ instead. It contains both fuzzyc2cpg and a component for querying code property graphs, as well as a few helpful examples to get started.**

A fuzzy parser for C/C++ that creates code property graphs according to the specification at https://github.com/ShiftLeftSecurity/codepropertygraph . This is a fork of the (now unmaintainted) version of Joern at https://github.com/octopus-platform/joern.

## Building the code

The build process has been verified on Linux and it should be possible 
to build on OS X and BSD systems as well. The build process requires
the following prerequisites:

* Java runtime 8
  - Link: http://openjdk.java.net/install/
* Scala build tool (sbt)
  - Link: https://www.scala-sbt.org/

Additional build-time dependencies are automatically downloaded as part
of the build process. To build fuzzyc2cpg issue the command `sbt stage`.

## Running

To run fuzzyc2cpg in order to produce a code property graph issue the
command
`./fuzzyc2cpg.sh <path/to/sourceCodeDirectory> --out <path/to/outputCpg>`.
