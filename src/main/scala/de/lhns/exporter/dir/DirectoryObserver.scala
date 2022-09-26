package de.lhns.exporter.dir

import cats.effect.IO
import cats.kernel.Monoid
import de.lhns.exporter.dir.DirectoryObserver.{DirStats, DirStatsCollection, DirStatsKey, FileStats}
import fs2.Stream
import fs2.io.file.{Files, Path}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

class DirectoryObserver(path: Path) {
  def scan: IO[DirStatsCollection] = IO.defer {
    val collectionStart = Instant.now()
    Files[IO].list(path)
      .map { file =>
        Stream.eval(Files[IO].getBasicFileAttributes(file))
          .attempt
          .flatMap(e => Stream.fromOption(e.toOption))
          .filter(_.isRegularFile)
          .map { attributes =>
            val fileStats = FileStats(
              name = file.fileName.toString,
              modified = Instant.ofEpochMilli(attributes.lastModifiedTime.toMillis),
              size = attributes.size
            )
            Map(
              DirStatsKey.fromFileStats(
                path = file,
                fileStats = fileStats
              ) -> DirStats.fromFileStats(
                fileStats = fileStats
              )
            )
          }
      }
      .parJoinUnbounded
      .append(Stream.emit(Map(DirStatsKey.default -> Monoid[DirStats].empty)))
      .compile
      .foldMonoid
      .map { groups =>
        val collectionEnd = Instant.now()
        DirStatsCollection(
          collectionStart = collectionStart,
          collectionEnd = collectionEnd,
          groups = groups
        )
      }
  }

  def observe(interval: FiniteDuration): Stream[IO, DirStatsCollection] =
    Stream.fixedRateStartImmediately[IO](interval)
      .evalMap(_ => scan.attempt)
      .flatMap(e => Stream.fromOption(e.toOption))
}

object DirectoryObserver {
  case class DirStatsCollection(
                                 collectionStart: Instant,
                                 collectionEnd: Instant,
                                 groups: Map[DirStatsKey, DirStats]
                               )

  case class DirStatsKey(
                          empty: Boolean,
                          hidden: Boolean
                        )

  object DirStatsKey {
    val default: DirStatsKey = DirStatsKey(empty = false, hidden = false)

    def fromFileStats(path: Path, fileStats: FileStats): DirStatsKey = DirStatsKey(
      empty = fileStats.size == 0,
      hidden = path.fileName.toString.startsWith(".")
    )
  }

  case class DirStats(
                       count: Long,
                       size: Long,
                       oldest: Option[FileStats],
                       newest: Option[FileStats],
                     )

  object DirStats {
    implicit val monoid: Monoid[DirStats] = Monoid.instance(DirStats(
      count = 0,
      size = 0,
      oldest = None,
      newest = None
    ), { (a, b) =>
      DirStats(
        count = a.count + b.count,
        size = a.size + b.size,
        oldest = (a.oldest ++ b.oldest).minByOption(_.modified),
        newest = (a.newest ++ b.newest).maxByOption(_.modified)
      )
    })

    def fromFileStats(fileStats: FileStats): DirStats = DirStats(
      count = 1,
      size = fileStats.size,
      oldest = Some(fileStats),
      newest = Some(fileStats)
    )
  }

  case class FileStats(
                        name: String,
                        modified: Instant,
                        size: Long
                      )
}
