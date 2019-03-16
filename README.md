[![Build Status](https://secure.travis-ci.org/ShiftLeftSecurity/fuzzyc2cpg.png?branch=master)](http://travis-ci.org/ShiftLeftSecurity/fuzzyc2cpg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.shiftleft/fuzzyc2cpg_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.shiftleft/fuzzyc2cpg_2.12)

# fuzzyc2cpg

A fuzzy parser for C/C++ that creates code property graphs.

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
