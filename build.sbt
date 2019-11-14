name := "fuzzyc2cpg"
organization := "io.shiftleft"
scalaVersion := "2.12.10"
enablePlugins(GitVersioning)

val cpgVersion = "0.10.127"
val antlrVersion = "4.7.2"

libraryDependencies ++= Seq(
  "com.github.scopt"     %% "scopt"                    % "3.7.0",
  "org.antlr"            %  "antlr4-runtime"           % antlrVersion,
  "io.shiftleft"         %% "codepropertygraph"        % cpgVersion,
  "io.shiftleft"         %% "codepropertygraph-protos" % cpgVersion,
  "ch.qos.logback"       %  "logback-classic"          % "1.2.3",
  "commons-cli"          %  "commons-cli"              % "1.4",
  "com.github.pathikrit" %% "better-files"             % "3.1.0",

  "com.novocode"         %  "junit-interface"          % "0.11"  % Test,
  "junit"                %  "junit"                    % "4.12"  % Test,
  "org.scalatest"        %% "scalatest"                % "3.0.3" % Test,
  "org.apache.tinkerpop" %  "tinkergraph-gremlin"      % "3.4.3" % Test,
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
  "Artifactory release local" at "https://shiftleft.jfrog.io/shiftleft/libs-release-local",
  "Apache public" at "https://repository.apache.org/content/groups/public/",
  "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/public",
  "Bedatadriven for SOOT dependencies" at "https://nexus.bedatadriven.com/content/groups/public"
)

scalacOptions ++= Seq("-deprecation", "-feature")
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
licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
