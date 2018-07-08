name := "fuzzyc2cpg"
organization := "io.shiftleft"

val cpgVersion = "0.0.0-SNAPSHOT"

// TODO: Remove this upon open-sourcing of the CPG

resolvers ++= Seq(
  "Artifactory snapshot local" at "https://shiftleft.jfrog.io/shiftleft/libs-snapshot-local",
  "Artifactory release local" at "https://shiftleft.jfrog.io/shiftleft/libs-release-local"
)

libraryDependencies += "org.antlr" % "antlr4-runtime" % "4.5.4"
libraryDependencies += "io.shiftleft" % "codepropertygraph" % cpgVersion
libraryDependencies += "io.shiftleft" % "codepropertygraph-protos" % cpgVersion
libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % Test
libraryDependencies += "junit" % "junit" % "4.12" % Test

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
