package de.lhns.exporter.dir

import cats.effect.IO
import cats.kernel.Semigroup
import de.lhns.exporter.dir.DirectoryObserver.DirStats
import de.lhns.exporter.dir.DirectoryObserver.DirStats.FileStats
import fs2.Stream
import fs2.io.file.{Files, Path}

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
              .attempt
              .flatMap(e => Stream.fromOption(e.toOption))
              .filter(_.isRegularFile)
              .map { attributes =>
                DirStats.single(
                  now = Instant.now(),
                  fileStats = FileStats(
                    modified = Instant.ofEpochMilli(attributes.lastModifiedTime.toMillis),
                    size = attributes.size
                  )
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
      .evalMap(_ => scan.attempt)
      .flatMap(e => Stream.fromOption(e.toOption))
}

object DirectoryObserver {
  case class DirStats(
                       collectionStart: Instant,
                       collectionEnd: Instant,
                       count: Long,
                       countEmpty: Long,
                       size: Long,
                       oldest: Option[FileStats],
                       newest: Option[FileStats],
                     )

  object DirStats {
    case class FileStats(
                          modified: Instant,
                          size: Long
                        )

    implicit val semigroup: Semigroup[DirStats] = Semigroup.instance { (a, b) =>
      DirStats(
        count = a.count + b.count,
        countEmpty = a.countEmpty + b.countEmpty,
        size = a.size + b.size,
        collectionStart = Seq(a.collectionStart, b.collectionStart).min,
        collectionEnd = Seq(a.collectionEnd, b.collectionEnd).max,
        oldest = (a.oldest ++ b.oldest).minByOption(_.modified),
        newest = (a.newest ++ b.newest).maxByOption(_.modified)
      )
    }

    def empty(now: Instant): DirStats = DirStats(
      count = 0,
      countEmpty = 0,
      size = 0,
      collectionStart = now,
      collectionEnd = now,
      oldest = None,
      newest = None
    )

    def single(
                now: Instant,
                fileStats: FileStats
              ): DirStats = DirStats(
      count = 1,
      countEmpty = if (fileStats.size == 0) 1 else 0,
      size = fileStats.size,
      collectionStart = now,
      collectionEnd = now,
      oldest = Some(fileStats),
      newest = Some(fileStats)
    )
  }
}
