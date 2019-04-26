dependencyOverrides += "com.puppycrawl.tools" % "checkstyle" % "7.3"
addSbtPlugin("com.etsy" % "sbt-checkstyle-plugin" % "3.1.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.5")
addSbtPlugin("com.simplytyped" % "sbt-antlr4" % "0.8.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.8.0")
addSbtPlugin("com.github.sbt" % "sbt-findbugs" % "2.0.0")
addSbtPlugin("io.shiftleft" % "sbt-ci-release-early"  % "1.0.15")

/* TODO upgrade sbt-git to 1.0.1 once that has been released including: https://github.com/sbt/sbt-git/pull/162
 */
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
