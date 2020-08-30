name := "fuzzyc2cpg"
organization := "io.shiftleft"
scalaVersion := "2.13.1"
enablePlugins(GitVersioning)

val cpgVersion = "0.11.400+17-400ca87e"
val antlrVersion = "4.7.2"

libraryDependencies ++= Seq(
  "com.github.scopt"     %% "scopt"                    % "3.7.1",
  "org.antlr"            %  "antlr4-runtime"           % antlrVersion,
  "io.shiftleft"         %% "codepropertygraph"        % cpgVersion,
  "io.shiftleft"         %% "codepropertygraph-protos" % cpgVersion,
  "io.shiftleft"         %% "semanticcpg"              % cpgVersion,

  "commons-cli"          %  "commons-cli"              % "1.4",
  "com.github.pathikrit" %% "better-files"             % "3.8.0",
  "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",

  "ch.qos.logback"       %  "logback-classic"          % "1.2.3" % "test,runtime",
  "com.novocode"         %  "junit-interface"          % "0.11"  % Test,
  "junit"                %  "junit"                    % "4.12"  % Test,
  "org.scalatest"        %% "scalatest"                % "3.0.8" % Test,
)

excludeDependencies ++= Seq(
  // This project uses Logback in place of Log4j
  ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl"),
  ExclusionRule("org.slf4j", "slf4j-simple")
)

// uncomment if you want to use a cpg version that has *just* been released
// (it takes a few hours until it syncs to maven central)
resolvers += "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/public"
ThisBuild / resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.bintrayRepo("shiftleft", "maven"),
  Resolver.bintrayRepo("mpollmeier", "maven"),
  "Artifactory release local" at "https://shiftleft.jfrog.io/shiftleft/libs-release-local",
  "Apache public" at "https://repository.apache.org/content/groups/public/",
  "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/public",
  "Bedatadriven for SOOT dependencies" at "https://nexus.bedatadriven.com/content/groups/public"
)

scalacOptions ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  // "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Xlint:nullary-override",           // Warn when non-nullary def f() overrides nullary def f.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  // "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
)

compile / javacOptions ++= Seq("-Xlint:all", "-Xlint:-cast", "-g")
Test / fork := true
testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v")

checkstyleConfigLocation := CheckstyleConfigLocation.File("config/checkstyle/google_checks.xml")
checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Info)

enablePlugins(Antlr4Plugin)
Antlr4 / antlr4PackageName := Some("io.shiftleft.fuzzyc2cpg")
Antlr4 / antlr4Version := antlrVersion
Antlr4 / javaSource := (sourceManaged in Compile).value

enablePlugins(JavaAppPackaging)

scmInfo := Some(ScmInfo(url("https://github.com/ShiftLeftSecurity/fuzzyc2cpg"),
                            "scm:git@github.com:ShiftLeftSecurity/fuzzyc2cpg.git"))
homepage := Some(url("https://github.com/ShiftLeftSecurity/fuzzyc2cpg/"))
licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
developers := List(
  /* sonatype requires this to be non-empty */
  Developer(
    "fabsx00",
    "Fabian Yamaguchi",
    "fabs@shiftleft.io",
    url("https://github.com/fabsx00")
  ),
  Developer(
    "ml86",
    "Markus Lottmann",
    "markus@shiftleft.io",
    url("https://github.com/ml86")
  ),
  Developer(
    "julianthome",
    "Julian Thome",
    "julian.thome.de@gmail.com",
    url("https://github.com/julianthome")
  )
)
publishTo := sonatypePublishToBundle.value
Global / useGpg := false
Global / onChangedBuildSource := ReloadOnSourceChanges
