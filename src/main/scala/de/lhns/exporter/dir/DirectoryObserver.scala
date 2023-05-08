package de.lhns.exporter.dir

import cats.effect.{IO, Ref}
import cats.kernel.Monoid
import cats.syntax.traverse._
import de.lhns.exporter.dir.Config.DirConfig
import de.lhns.exporter.dir.DirectoryObserver.{DirStats, DirStatsCollection, DirStatsKey, FileStats}
import fs2.Stream
import fs2.io.file.{Files, Path}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.chaining._

class DirectoryObserver(dirConfig: DirConfig) {
  private def finiteDurationToInstant(finiteDuration: FiniteDuration): Instant =
    Instant.ofEpochMilli(finiteDuration.toMillis)

  private def scanPath(dirPath: Path): IO[Map[DirStatsKey, DirStats]] = {
    val emptyMap = IO.pure(Map.empty[DirStatsKey, DirStats])
    Files[IO].list(dirPath)
      .pipe(stream =>
        if (dirConfig.includeOrDefault.isEmpty && dirConfig.excludeOrDefault.isEmpty)
          stream
        else
          stream.filter { file =>
            val fileName = file.fileName.toString
            (dirConfig.includeOrDefault.isEmpty || dirConfig.includeOrDefault.exists(fileName.matches)) &&
              !dirConfig.excludeOrDefault.exists(fileName.matches)
          }
      )
      .flatMap[IO, Map[DirStatsKey, DirStats]] { path =>
        Stream.eval(Files[IO].getBasicFileAttributes(path))
          .attempt
          .flatMap(e => Stream.fromOption(e.toOption))
          .evalMap { attributes =>
            if (attributes.isRegularFile) {
              val fileStats = FileStats(
                name = path.fileName.toString,
                modified = finiteDurationToInstant(attributes.lastModifiedTime),
                size = attributes.size
              )
              IO.pure(Map(
                DirStatsKey.fromFileStats(
                  filePath = path,
                  fileStats = fileStats
                ) -> DirStats.fromFileStats(
                  fileStats = fileStats
                )
              ))
            } else if (dirConfig.recursiveOrDefault) {
              scanPath(path)
            } else {
              emptyMap
            }
          }
      }
      .append(Stream.emit(Map(
        DirStatsKey.default(dirPath) -> Monoid[DirStats].empty
      )))
      .compile
      .foldMonoid
  }

  private def scan: IO[DirStatsCollection] =
    for {
      collectionStart <- IO.realTimeInstant
      groups <- scanPath(dirConfig.path)
      dirAttributes <- Files[IO].getBasicFileAttributes(dirConfig.path)
      collectionEnd <- IO.realTimeInstant
    } yield DirStatsCollection(
      dirConfig = dirConfig,
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
      _ <- Stream.eval(adaptiveIntervalMultiplier.map { adaptiveIntervalMultiplier =>
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
                                 dirConfig: DirConfig,
                                 collectionStart: Instant,
                                 collectionEnd: Instant,
                                 modified: Instant,
                                 groups: Map[DirStatsKey, DirStats]
                               )

  case class DirStatsKey(
                          path: Path,
                          empty: Boolean,
                          hidden: Boolean
                        )

  object DirStatsKey {
    def default(dirPath: Path): DirStatsKey = DirStatsKey(
      path = dirPath,
      empty = false,
      hidden = false
    )

    def fromFileStats(filePath: Path, fileStats: FileStats): DirStatsKey = DirStatsKey(
      path = filePath.parent.get,
      empty = fileStats.size == 0,
      hidden = filePath.fileName.toString.startsWith(".")
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
