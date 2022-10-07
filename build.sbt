ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

val V = new {
  val circe = "0.14.3"
  val circeConfig = "0.10.0"
  val http4s = "0.23.16"
  val http4sJdkHttpClient = "0.7.0"
  val logbackClassic = "1.4.3"
  val munit = "0.7.29"
  val munitTaglessFinal = "0.2.0"
  val opentelemetry = "1.18.0"
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
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
      "org.http4s" %% "http4s-client" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % V.http4sJdkHttpClient,
    )
  )
