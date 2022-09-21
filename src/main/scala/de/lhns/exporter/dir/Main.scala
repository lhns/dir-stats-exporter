package de.lhns.exporter.dir

import cats.Functor
import cats.data.OptionT
import cats.effect.std.{Queue, QueueSink}
import cats.effect.{ExitCode, IO, IOApp, Resource, Sync}
import fs2.io.file.Path
import fs2.{Pipe, Stream}
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{Meter, ObservableLongGauge}
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader
import cats.syntax.contravariant._
import cats.syntax.functor._

import java.time.Instant
import scala.concurrent.duration.DurationInt

object Main extends IOApp {
  def meterProviderResource(endpoint: String): Resource[IO, SdkMeterProvider] = Resource.liftK(IO {
    SdkMeterProvider.builder()
      .registerMetricReader(
        PeriodicMetricReader.builder(
          OtlpGrpcMetricExporter.builder()
            .setEndpoint("")
            .build()
        ).build()
      )
      .build()
  })

  case class Config(dir: Path)

  override def run(args: List[String]): IO[ExitCode] =
    applicationResource(Config(Path(""))).use(_ => IO.never)

  @specialized(Long)
  case class MetricsRecord[A](value: A, attributes: Attributes)

  object MetricsRecord {
    implicit val functor: Functor[MetricsRecord] = new Functor[MetricsRecord] {
      override def map[A, B](fa: MetricsRecord[A])(f: A => B): MetricsRecord[B] =
        MetricsRecord(f(fa.value), fa.attributes)
    }
  }

  def gauge[A](meter: Meter, name: String, f: A => IO[Long]): Resource[IO, QueueSink[IO, MetricsRecord[A]]] = Resource.make {
    for {
      queue <- Queue.circularBuffer[IO, MetricsRecord[A]](1)
      gauge = meter.gaugeBuilder(name).ofLongs().buildWithCallback { measurement =>
        val recordOption = OptionT(queue.tryTake)
          .semiflatMap(e => f(e.value).map(MetricsRecord(_, e.attributes)))
          .value
          .unsafeRunSync()(runtime)
        recordOption.foreach { record =>
          measurement.record(
            record.value,
            record.attributes
          )
        }
      }
    } yield (gauge, queue)
  } {
    case (gauge, _) => IO {
      gauge.close()
    }
  }.map {
    case (_, queue) => queue
  }

  def gauge(meter: Meter, name: String): Resource[IO, QueueSink[IO, MetricsRecord[Long]]] =
    gauge(meter, name, e => IO.pure(e))

  private val prefix = "dir_files"

  private def applicationResource(config: Config): Resource[IO, Unit] =
    for {
      meterProvider <- meterProviderResource("")
      meter = meterProvider.get("dir-stats-exporter")
      gaugeLastSeen <- gauge[Instant](meter, s"${prefix}_last_seen", e => IO.pure(e.toEpochMilli))
      gaugeCount <- gauge(meter, s"${prefix}_count")
      gaugeOldestTs <- gauge[Instant](meter, s"${prefix}_oldest_ts_millis", e => IO.pure(e.toEpochMilli))
      gaugeNewestTs <- gauge[Instant](meter, s"${prefix}_newest_ts_millis", e => IO.pure(e.toEpochMilli))
      gaugeOldestAge <- gauge(meter, s"${prefix}_oldest_age_seconds")
      gaugeNewestAge <- gauge(meter, s"${prefix}_newest_age_seconds")
      _ <- Resource.eval(new DirectoryObserver(Path(""))
        .observe(10.seconds)
        .evalMap { stats =>
          val attributes =
            Attributes.builder()
              .put("path", "test")
              .build()
          for {
            _ <- gaugeLastSeen.offer(MetricsRecord(stats.lastSeen, attributes))
            _ <- gaugeCount.offer(MetricsRecord(stats.count, attributes))
            _ <- gaugeOldestTs.offer(MetricsRecord(stats.oldest, attributes))
            _ <- gaugeNewestTs.offer(MetricsRecord(stats.newest, attributes))
            _ <- gaugeOldestAge.offer(MetricsRecord(stats.oldest.map(stats.lastSeen.getEpochSecond - _.getEpochSecond), attributes))
            _ <- gaugeNewestAge.offer(MetricsRecord(stats.newest.map(stats.lastSeen.getEpochSecond - _.getEpochSecond), attributes))
          } yield ()
        }
        .compile.drain
      )
      /*_ = {

        meter.gaugeBuilder("dir_files_last_seen").ofLongs()
        meter.gaugeBuilder("dir_files_count").ofLongs().buildWithCallback { measurement =>
          val option = lastSeenQueue.tryTake.unsafeRunSync()(runtime)
          option.foreach(measurement.)
        }
        meter.gaugeBuilder("dir_files_oldest_ts")
        meter.gaugeBuilder("dir_files_newest_ts")
        meter.gaugeBuilder("dir_files_oldest_age_seconds")
        meter.gaugeBuilder("dir_files_newest_age_seconds")

      }*/
    } yield ()
}
