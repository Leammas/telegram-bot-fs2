val http4sVersion = "0.19.0-M1"
val log4CatsVersion = "0.0.6"
val slf4jVersion = "1.7.25"
val kindProjectorVersion = "0.9.6"
val circeVersion = "0.9.2"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3" % Test
val magnolia = "com.propensive" %% "magnolia" % "0.6.1" % Test
val doobieVersion = "0.6.0-M3"
val doobie = Seq(
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion
)
val testContainers = Seq(
  "com.dimafeng" %% "testcontainers-scala" % "0.14.0" % Test,
  "org.testcontainers" % "postgresql" % "1.6.0" % Test
)
val catsMTL = "org.typelevel" %% "cats-mtl-core" % "0.2.3" % Test
val typesafeConfig = "com.typesafe" % "config" % "1.3.2"
val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.9.0"
val monocleVersion = "1.5.1-cats"
val monocle = Seq(
  "com.github.julien-truffaut" %% "monocle-core" % monocleVersion % Test,
  "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion % Test
)
val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"

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
    catsMTL
  ) ++ doobie ++ testContainers ++ monocle,
  sources in (Compile, doc) := Nil,
  organization := "ru.pavkin",
  name := "telegram-bot-fs2",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.8",
)

def module(name: String): Project =
  Project(id = name, base = file(s"modules/$name"))
    .settings(
      baseSettings
    )

lazy val tgbot =
  module("tgbot")

lazy val issueTracker =
  module("issue-tracker")