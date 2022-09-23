package de.lhns.exporter.dir

import cats.effect.{ExitCode, IO, IOApp, Resource}
import de.lhns.exporter.dir.DirectoryObserver.DirStats.DirStatsCounters
import fs2.Stream
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    applicationResource(Config.fromEnv).use(_ => IO.never)

  def meterProviderResource(endpoint: String): Resource[IO, SdkMeterProvider] = Resource.liftK(IO {
    SdkMeterProvider.builder()
      .registerMetricReader(
        PeriodicMetricReader.builder(
          OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .build()
        ).build()
      )
      .build()
  })

  private def applicationResource(config: Config): Resource[IO, Unit] =
    for {
      meterProvider <- meterProviderResource(config.endpoint)
      meter = meterProvider.get("dir-stats-exporter")
      counters = new DirStatsCounters(meter, config.prefixOrDefault)
      _ <- Resource.eval {
        Stream.emits(config.directories)
          .map { directory =>
            new DirectoryObserver(
              path = directory.path,
              includeHidden = directory.includeHiddenOrDefault
            )
              .observe(directory.interval.getOrElse(config.defaultInterval))
              .map(dirStats => (directory, dirStats))
          }
          .parJoinUnbounded
          .map {
            case (directory, dirStats) =>
              counters.add(dirStats, directory.path, directory.tagsOrDefault)
          }
          .compile
          .drain
      }
    } yield ()
}
