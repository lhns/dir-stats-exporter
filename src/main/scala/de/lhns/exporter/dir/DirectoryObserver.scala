package de.lhns.exporter.dir

import cats.effect.IO
import cats.kernel.Semigroup
import de.lhns.exporter.dir.DirectoryObserver.DirStats
import de.lhns.exporter.dir.DirectoryObserver.DirStats.FileStats
import fs2.Stream
import fs2.io.file.{Files, Path}
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.chaining._

class DirectoryObserver(
                         path: Path,
                         includeHidden: Boolean
                       ) {
  def scan: IO[DirStats] = {
    Stream.eval(IO(DirStats.empty(Instant.now)))
      .append(
        Files[IO].list(path)
          .pipe(stream =>
            if (includeHidden) stream
            else stream.filter(!_.fileName.toString.startsWith("."))
          )
          .map { file =>
            Stream.eval(Files[IO].getBasicFileAttributes(file))
              .filter(_.isRegularFile)
              .map { attributes =>
                val fileStats = FileStats(
                  modified = Instant.ofEpochMilli(attributes.lastModifiedTime.toMillis),
                  size = attributes.size
                )
                DirStats(
                  oldest = Some(fileStats),
                  newest = Some(fileStats),
                  size = fileStats.size,
                  countEmpty = if (fileStats.size == 0) 1 else 0
                )
              }
          }
          .parJoinUnbounded
      )
      .compile
      .foldSemigroup
      .map(_.get)
  }

  def observe(interval: FiniteDuration): Stream[IO, DirStats] =
    Stream.fixedRateStartImmediately[IO](interval)
      .evalMap(_ => scan)
}

object DirectoryObserver {
  case class DirStats(
                       oldest: Option[FileStats],
                       newest: Option[FileStats],
                       size: Long,
                       countEmpty: Long,
                       count: Long = 1
                     )

  object DirStats {
    case class FileStats(
                          modified: Instant,
                          size: Long
                        )

    implicit val semigroup: Semigroup[DirStats] = Semigroup.instance { (a, b) =>
      DirStats(
        oldest = (a.oldest ++ b.oldest).minByOption(_.modified),
        newest = (a.newest ++ b.newest).maxByOption(_.modified),
        size = a.size + b.size,
        countEmpty = a.countEmpty + b.countEmpty,
        count = a.count + b.count,
      )
    }

    def empty(now: Instant): DirStats = DirStats(
      oldest = None,
      newest = None,
      size = 0,
      countEmpty = 0,
      count = 0
    )

    class DirStatsCounters(meter: Meter, prefix: String) {
      private val unitSeconds = "seconds"
      private val unitBytes = "bytes"

      private val counterOldestTs = meter.upDownCounterBuilder(s"${prefix}_oldest_ts").setUnit(unitSeconds).build()
      private val counterOldestAge = meter.upDownCounterBuilder(s"${prefix}_oldest_age").setUnit(unitSeconds).build()
      private val counterOldestBytes = meter.upDownCounterBuilder(s"${prefix}_oldest_bytes").setUnit(unitBytes).build()
      private val counterNewestTs = meter.upDownCounterBuilder(s"${prefix}_newest_ts").setUnit(unitSeconds).build()
      private val counterNewestAge = meter.upDownCounterBuilder(s"${prefix}_newest_age").setUnit(unitSeconds).build()
      private val counterNewestBytes = meter.upDownCounterBuilder(s"${prefix}_newest_bytes").setUnit(unitBytes).build()
      private val counterBytes = meter.upDownCounterBuilder(s"${prefix}_bytes").setUnit(unitBytes).build()
      private val counterCountEmpty = meter.upDownCounterBuilder(s"${prefix}_count_empty").build()
      private val counterCount = meter.upDownCounterBuilder(s"${prefix}_count").build()

      def add(dirStats: DirStats, path: Path, tags: Map[String, String]): Unit = {
        val attributes = tags.foldLeft(
          Attributes.builder()
            .put("path", path.toString)
        ) {
          case (builder, (key, value)) => builder.put(key, value)
        }.build()

        val now = Instant.now()

        dirStats.oldest.foreach { oldest =>
          counterOldestTs.add(oldest.modified.getEpochSecond, attributes)
          counterOldestAge.add(now.getEpochSecond - oldest.modified.getEpochSecond, attributes)
          counterOldestBytes.add(oldest.size, attributes)
        }

        dirStats.newest.foreach { newest =>
          counterNewestTs.add(newest.modified.getEpochSecond, attributes)
          counterNewestAge.add(now.getEpochSecond - newest.modified.getEpochSecond, attributes)
          counterNewestBytes.add(newest.size, attributes)
        }

        counterBytes.add(dirStats.size, attributes)
        counterCountEmpty.add(dirStats.countEmpty, attributes)
        counterCount.add(dirStats.count, attributes)
      }
    }
  }
}
