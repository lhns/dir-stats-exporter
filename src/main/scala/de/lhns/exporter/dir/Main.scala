package de.lhns.exporter.dir

import cats.effect.{ExitCode, IO, IOApp, Resource}
import fs2.Stream
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.`export`.MetricExporter

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    applicationResource(Config.fromEnv).use(_ => IO.never)

  def makeMetricExporter(endpoint: String): Resource[IO, MetricExporter] = Resource.liftK(IO {
    OtlpGrpcMetricExporter.builder()
      .setEndpoint(endpoint)
      .build()
  })

  private def applicationResource(config: Config): Resource[IO, Unit] =
    for {
      metricExporter <- makeMetricExporter(config.endpoint)
      dirStatsMetricData = new DirStatsMetricData(config.prefixOrDefault)
      _ <- Resource.eval {
        Stream.emits(config.directories)
          .map { directory =>
            new DirectoryObserver(
              path = directory.path,
              filter = directory.filter
            )
              .observe(directory.interval.getOrElse(config.defaultInterval))
              .map(dirStats => (directory, dirStats))
          }
          .parJoinUnbounded
          .flatMap {
            case (directory, dirStatsCollection) =>
              Stream.iterable(
                dirStatsMetricData.toMetricData(dirStatsCollection, directory.path, directory.tagsOrDefault)
              )
          }
          .groupWithin(8192, 10.seconds)
          .map { metricDataChunk =>
            metricExporter.`export`(metricDataChunk.toList.asJavaCollection)
          }
          .compile
          .drain
      }
    } yield ()
}
