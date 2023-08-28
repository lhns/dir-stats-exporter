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

  private def scanPath(dirConfig: DirConfig, depth: Int): IO[Seq[DirStatsCollection]] =
    Ref.of[IO, Seq[DirStatsCollection]](Seq.empty).flatMap { childStatCollectionsRef =>
      val collectGroups: IO[Map[DirStatsKey, DirStats]] = Files[IO].list(dirConfig.path)
        .pipe(stream =>
          if (
            dirConfig.includeOrDefault.isEmpty &&
              dirConfig.excludeOrDefault.isEmpty
          )
            stream
          else
            stream.filter { file =>
              val fileName = file.fileName.toString
              (dirConfig.includeOrDefault.isEmpty || dirConfig.includeOrDefault.exists(fileName.matches)) &&
                !dirConfig.excludeOrDefault.exists(fileName.matches)
            }
        )
        .flatMap[IO, Stream[IO, Map[DirStatsKey, DirStats]]] { path =>
          Stream.eval(Files[IO].getBasicFileAttributes(path))
            .attempt
            .flatMap(e => Stream.fromOption(e.toOption))
            .map { attributes =>
              def shouldScanFile =
                attributes.isRegularFile &&
                  dirConfig.minDepth.forall(depth >= _)

              def shouldScanDirectory =
                attributes.isDirectory &&
                  dirConfig.recursiveOrDefault &&
                  dirConfig.maxDepth.forall(depth < _) &&
                  !dirConfig.excludeDirPathOrDefault.exists(path.toString.matches)

              if (shouldScanFile) {
                val fileStats = FileStats(
                  name = path.fileName.toString,
                  modified = finiteDurationToInstant(attributes.lastModifiedTime),
                  size = attributes.size
                )
                Stream.emit(Map(
                  DirStatsKey.fromFileStats(
                    filePath = path,
                    fileStats = fileStats
                  ) -> DirStats.fromFileStats(
                    fileStats = fileStats
                  )
                ))
              } else if (shouldScanDirectory) {
                Stream.exec {
                  scanPath(dirConfig.withPath(path), depth + 1)
                    .flatMap { childStatCollections =>
                      childStatCollectionsRef.update(_ ++ childStatCollections)
                    }
                }
              } else {
                Stream.empty
              }
            }
        }
        .parJoin(dirConfig.parallelismOrDefault)
        .append(Stream.emit(Map(
          DirStatsKey.default -> Monoid[DirStats].empty
        )))
        .compile
        .foldMonoid

      for {
        collectionStart <- IO.realTimeInstant
        groups <- collectGroups
        dirAttributes <- Files[IO].getBasicFileAttributes(dirConfig.path)
        collectionEnd <- IO.realTimeInstant
        childStatCollections <- childStatCollectionsRef.get
      } yield DirStatsCollection(
        dirConfig = dirConfig,
        collectionStart = collectionStart,
        collectionEnd = collectionEnd,
        modified = finiteDurationToInstant(dirAttributes.lastModifiedTime),
        groups = groups
      ) +: childStatCollections
    }

  private def scan: IO[Seq[DirStatsCollection]] =
    scanPath(dirConfig, 0)

  def observe(
               interval: FiniteDuration,
               adaptiveIntervalMultiplier: Option[Double]
             ): Stream[IO, Seq[DirStatsCollection]] = {
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
                          empty: Boolean,
                          hidden: Boolean
                        )

  object DirStatsKey {
    val default: DirStatsKey = DirStatsKey(
      empty = false,
      hidden = false
    )

    def fromFileStats(filePath: Path, fileStats: FileStats): DirStatsKey = DirStatsKey(
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
