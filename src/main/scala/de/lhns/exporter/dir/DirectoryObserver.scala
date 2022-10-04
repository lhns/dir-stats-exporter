package de.lhns.exporter.dir

import cats.effect.{IO, Ref}
import cats.kernel.Monoid
import cats.syntax.traverse._
import de.lhns.exporter.dir.DirectoryObserver.{DirStats, DirStatsCollection, DirStatsKey, FileStats}
import fs2.Stream
import fs2.io.file.{Files, Path}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.chaining._

class DirectoryObserver(
                         path: Path,
                         filter: Option[String]
                       ) {
  private def finiteDurationToInstant(finiteDuration: FiniteDuration): Instant =
    Instant.ofEpochMilli(finiteDuration.toMillis)

  def scan: IO[DirStatsCollection] =
    for {
      collectionStart <- IO.realTimeInstant
      groups <- Files[IO].list(path)
        .pipe(stream => filter.fold(stream)(regex => stream.filter(_.fileName.toString.matches(regex))))
        .flatMap { file =>
          Stream.eval(Files[IO].getBasicFileAttributes(file))
            .attempt
            .flatMap(e => Stream.fromOption(e.toOption))
            .filter(_.isRegularFile)
            .map { attributes =>
              val fileStats = FileStats(
                name = file.fileName.toString,
                modified = finiteDurationToInstant(attributes.lastModifiedTime),
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
        .append(Stream.emit(Map(DirStatsKey.default -> Monoid[DirStats].empty)))
        .compile
        .foldMonoid
      dirAttributes <- Files[IO].getBasicFileAttributes(path)
      collectionEnd <- IO.realTimeInstant
    } yield DirStatsCollection(
      collectionStart = collectionStart,
      collectionEnd = collectionEnd,
      modified = finiteDurationToInstant(dirAttributes.lastModifiedTime),
      groups = groups
    )

  def observe(
               interval: FiniteDuration,
               adaptiveIntervalMultiplier: Option[Double]
             ): Stream[IO, DirStatsCollection] = {
    for {
      delayUntilRef <- Stream.eval(Ref.of[IO, Option[Instant]](None))
      _ <- Stream.fixedRateStartImmediately[IO](interval)
      _ <- Stream.eval {
        for {
          now <- IO.realTimeInstant
          delayUntil <- delayUntilRef.get
          _ <- delayUntil.filter(_.isAfter(now)).map { delayUntil =>
            val delay: Long = delayUntil.toEpochMilli - now.toEpochMilli
            IO.sleep(FiniteDuration(delay, TimeUnit.MILLISECONDS))
          }.sequence
        } yield ()
      }
      timeBefore <- Stream.eval(IO.realTimeInstant)
      resultOrError <- Stream.eval(scan.attempt)
      timeAfter <- Stream.eval(IO.realTimeInstant)
      duration = FiniteDuration(timeAfter.toEpochMilli - timeBefore.toEpochMilli, TimeUnit.MILLISECONDS)
      _ <- Stream.eval(adaptiveIntervalMultiplier.filter(_ => duration > interval).map { adaptiveIntervalMultiplier =>
        val delay: Long = (duration.toMillis * adaptiveIntervalMultiplier).toLong
        delayUntilRef.set(Some(timeBefore.plusMillis(delay)))
      }.sequence)
      result <- Stream.fromOption(resultOrError.toOption)
    } yield
      result
  }
}

object DirectoryObserver {
  case class DirStatsCollection(
                                 collectionStart: Instant,
                                 collectionEnd: Instant,
                                 modified: Instant,
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
