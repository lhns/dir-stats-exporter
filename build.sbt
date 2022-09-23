ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.9"

val V = new {
  val circe = "0.14.2"
  val circeConfig = "0.8.0"
  val http4s = "0.23.15"
  val http4sJdkHttpClient = "0.7.0"
  val opentelemetry = "1.18.0"
  val proxyVole = "1.0.17"
  val trustmanagerUtils = "0.3.4"
}

lazy val root = (project in file("."))
  .settings(
    name := "dir-stats-exporter",

    libraryDependencies ++= Seq(
      "de.lolhens" %% "scala-trustmanager-utils" % V.trustmanagerUtils,
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-config" % V.circeConfig,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % V.opentelemetry,
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % V.proxyVole,
      "org.http4s" %% "http4s-dsl" % V.http4s,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-client" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % V.http4sJdkHttpClient,
    )
  )
