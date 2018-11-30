name := "fuzzyc2cpg"
organization := "io.shiftleft"
scalaVersion := "2.12.7"

val cpgVersion = "0.9.76"

libraryDependencies ++= Seq(
  "org.antlr" % "antlr4-runtime" % "4.5.4",
  "io.shiftleft" % "codepropertygraph" % cpgVersion,
  "io.shiftleft" % "codepropertygraph-protos" % cpgVersion,
  "io.shiftleft" % "cpgloader-tinkergraph" % cpgVersion,
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.novocode" % "junit-interface" % "0.11" % Test,
  "junit" % "junit" % "4.12" % Test,
  "org.scalatest" %% "scalatest" % "3.0.3" % Test,
)

// uncomment if you want to use a cpg version that has *just* been released
// (it takes a few hours until it syncs to maven central)
resolvers += "Sonatype OSS" at "https://oss.sonatype.org/content/repositories/public"

scalacOptions ++= Seq("-deprecation", "-feature")
compile / javacOptions ++= Seq("-Xlint:all", "-Xlint:-cast", "-g")
Test / javaOptions ++= Seq("-Dlog4j2.configurationFile=../cpg2sp/src/test/resources/log4j2-test.xml")
Test / fork := true
testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v")

checkstyleConfigLocation := CheckstyleConfigLocation.File("config/checkstyle/google_checks.xml")
checkstyleSeverityLevel := Some(CheckstyleSeverityLevel.Info)
// checkstyle := checkstyle.triggeredBy(Compile / compile).value

enablePlugins(Antlr4Plugin)
Antlr4 / antlr4PackageName := Some("io.shiftleft.fuzzyc2cpg")
Antlr4 / antlr4Version := "4.7"
Antlr4 / javaSource := (sourceManaged in Compile).value

enablePlugins(JavaAppPackaging)

publishTo := Some("releases" at "https://shiftleft.jfrog.io/shiftleft/libs-release-local")
