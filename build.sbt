val http4sVersion = "0.19.0-M1"
val log4CatsVersion = "0.0.6"
val slf4jVersion = "1.7.25"
val kindProjectorVersion = "0.9.6"
val circeVersion = "0.9.2"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
val magnolia = "com.propensive" %% "magnolia" % "0.6.1" % Test
val doobieVersion = "0.6.0"
val doobie = Seq(
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,
  "org.tpolecat" %% "doobie-hikari" % doobieVersion
)
val testContainers = Seq(
  "com.dimafeng" %% "testcontainers-scala" % "0.14.0" % Test,
  "org.testcontainers" % "postgresql" % "1.6.0" % Test
)
val catsMTL = "org.typelevel" %% "cats-mtl-core" % "0.2.3"
val catsTagless = "org.typelevel" %% "cats-tagless-macros" % "0.2.0"
val typesafeConfig = "com.typesafe" % "config" % "1.3.2"
val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.9.0"
val monocleVersion = "1.5.1-cats"
val monocle = Seq(
  "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
  "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion
)
val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"

val aecorVersion = "0.18.0"

val aecor = Seq(
  "io.aecor" %% "core" % aecorVersion,
  "io.aecor" %% "schedule" % aecorVersion,
  "io.aecor" %% "akka-cluster-runtime" % aecorVersion,
  "io.aecor" %% "distributed-processing" % aecorVersion,
  "io.aecor" %% "boopickle-wire-protocol" % aecorVersion,
  "io.aecor" %% "aecor-postgres-journal" % "0.3.0",
  "io.aecor" %% "test-kit" % aecorVersion % Test
)

lazy val baseSettings = Seq(
  libraryDependencies ++= Seq(
    compilerPlugin("org.spire-math" %% "kind-projector" % kindProjectorVersion),
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.chrisdavenport" %% "log4cats-core" % log4CatsVersion,
    "io.chrisdavenport" %% "log4cats-slf4j" % log4CatsVersion,
    "org.slf4j" % "slf4j-simple" % slf4jVersion,
    scalaCheck,
    scalaTest,
    magnolia,
    typesafeConfig,
    pureconfig,
    catsEffect,
    catsMTL,
    catsTagless
  ) ++ doobie ++ testContainers ++ monocle,
  scalacOptions += "-Ypartial-unification",
  sources in (Compile, doc) := Nil,
  organization := "ru.pavkin",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.8",
  addCompilerPlugin(
    "org.scalameta" % "paradise" % "3.0.0-M11" cross CrossVersion.full)
)

def module(name: String): Project =
  Project(id = name, base = file(s"modules/$name"))
    .settings(
      baseSettings
    )

lazy val common =
  module("common")

val commonFullDep = common % "test->test;compile->compile"

lazy val tgbot =
  module("tgbot").dependsOn(commonFullDep )

lazy val issueTracker =
  module("issue-tracker")
    .settings(
      libraryDependencies ++= aecor
    )
    .dependsOn(commonFullDep)

lazy val tests =
  module("tests").dependsOn(commonFullDep, issueTracker % "test->test", tgbot % "test->test")
