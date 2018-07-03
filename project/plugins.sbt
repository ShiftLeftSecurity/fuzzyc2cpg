// for our clone of sbt-checkstyle-plugin
resolvers ++= Seq(Resolver.mavenLocal)

dependencyOverrides += "com.puppycrawl.tools" % "checkstyle" % "7.3"
addSbtPlugin("com.etsy" % "sbt-checkstyle-plugin" % "3.1.1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.2")
addSbtPlugin("com.simplytyped" % "sbt-antlr4" % "0.8.1")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.8.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("com.github.sbt" % "sbt-findbugs" % "2.0.0")
