ThisBuild / scalaVersion := "2.13.10"

val V = new {
  val betterMonadicFor = "0.3.1"
  val circe = "0.14.5"
  val circeConfig = "0.10.0"
  val http4s = "0.23.18"
  val logbackClassic = "1.4.5"
  val munit = "0.7.29"
  val munitTaglessFinal = "0.2.0"
  val opentelemetry = "1.24.0"
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  version := {
    val Tag = "refs/tags/v?([0-9]+(?:\\.[0-9]+)+(?:[+-].*)?)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor),
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    "de.lolhens" %% "munit-tagless-final" % V.munitTaglessFinal % Test,
    "org.scalameta" %% "munit" % V.munit % Test
  ),
  testFrameworks += new TestFramework("munit.Framework"),
  assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",
  assembly / assemblyOption := (assembly / assemblyOption).value
    .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),
  assembly / assemblyMergeStrategy := {
    case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "dir-stats-exporter",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % V.logbackClassic,
      "com.hunorkovacs" %% "circe-config" % V.circeConfig,
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % V.opentelemetry,
      "org.http4s" %% "http4s-dsl" % V.http4s,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-client" % V.http4s
    )
  )
