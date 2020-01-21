import sbt.Keys._

name := "scalingua-root"
version := "0.9.1-SNAPSHOT"
crossPaths := true

publishArtifact := false
publishTo := Some(Resolver.file("Transient repository", file("/tmp/unused")))

val common = Seq(
  organization := "ru.makkarpov",
  version := (version in LocalRootProject).value,

  crossPaths := true,
  scalaVersion := "2.12.10", //should be the same for all projects for cross-build to work
  crossScalaVersions := Seq("2.10.7", "2.11.12", scalaVersion.value, "2.13.1"),
  scalacOptions ++= Seq( "-Xfatal-warnings", "-feature", "-deprecation" ),

  libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.0" % Test,

  publishArtifact in Test := false,
  publishMavenStyle := true,

  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/makkarpov/scalingua")),
  organizationName := "Maxim Karpov",
  organizationHomepage := Some(url("https://github.com/makkarpov")),
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/makkarpov/scalingua"),
    connection = "scm:git://github.com/makkarpov/scalingua.git"
  )),

  // Seems that SBT key `developers` is producing incorrect results
  pomExtra := {
    <developers>
      <developer>
        <id>makkarpov</id>
        <name>Maxim Karpov</name>
        <url>https://github.com/makkarpov</url>
      </developer>
    </developers>
  },

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val core = project
  .settings(common:_*)
  .settings(
    name := "Scalingua Core",
    normalizedName := "scalingua-core",
    description := "A minimal set of runtime classes for Scalingua"
  )

lazy val scalingua = project
  .enablePlugins(ParserGenerator, AssemblyPlugin)
  .settings(common:_*)
  .settings(
    name := "Scalingua",
    normalizedName := "scalingua",
    description := "A simple gettext-like internationalization library for Scala",

    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.github.vbmacher" % "java-cup-runtime" % "11b-20160615",
      "com.grack" % "nanojson" % "1.2"
    ),

    libraryDependencies ++= {
      CrossVersion.binaryScalaVersion(scalaVersion.value) match {
        case "2.10" => Seq(
          compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
          "org.scalamacros" %% "quasiquotes" % "2.1.1"
        )
        case _ => Nil
      }
    },

    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("java_cup.runtime.**" -> "ru.makkarpov.scalingua.pofile.shaded_javacup.@1").inAll
    ),

    // include only CUP:
    assemblyExcludedJars in assembly := (fullClasspath in assembly).value.filterNot { f =>
      f.data.getName.contains("java-cup-runtime")
    }
  )
  .dependsOn(core)

lazy val scalingua_shadedCup = project.in(file("target/shaded-cup"))
    .settings(common:_*)
    .settings(
      name := "Scalingua shaded",
      normalizedName := "scalingua-shaded",
      description := "Scalingua with shaded CUP runtime to prevent conflicts",

      packageBin in Compile := (assembly in (scalingua, Compile)).value,
      libraryDependencies := (libraryDependencies in scalingua).value.filterNot(_.name.contains("java-cup"))
    )
    .dependsOn(scalingua.dependencies:_*)

lazy val play = project
  .settings(common:_*)
  .settings(
    name := "Scalingua Play module",
    normalizedName := "scalingua-play",
    description := "An integration module for Play Framework",

    // Recent versions of Play supports only recent version of Scala.
    crossScalaVersions := Seq(scalaVersion.value, "2.13.1"),

    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "twirl-api" % "1.5.0",
      "com.typesafe.play" %% "play" % "2.8.0"
    )
  ).dependsOn(scalingua)

lazy val plugin = project
  .in(file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .settings(common:_*)
  .settings(
    name := "Scalingua SBT plugin",
    normalizedName := "scalingua-sbt",
    description := "SBT plugin that compiles locales, manages locations of *.pot files and so on",

    crossPaths := false,
    crossScalaVersions := Seq(scalaVersion.value),

    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M", "-XX:MaxPermSize=256M", "-Dscalingua.version=" + (version in LocalRootProject).value
    ),
    scriptedBufferLog := false,
    scripted := scripted.dependsOn(scalingua / publishLocal, core / publishLocal).evaluated,
    pluginCrossBuild / sbtVersion := "1.2.8", //https://github.com/sbt/sbt/issues/5049
  ).dependsOn(scalingua)
