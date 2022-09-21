package de.lhns.exporter.dir

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.kernel.Semigroup
import de.lhns.exporter.dir.DirectoryObserver.Stats
import fs2.io.file.{Files, Path}
import fs2.Stream

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

class DirectoryObserver(path: Path) {
  def scan: IO[Stats] = {
    Stream.eval(IO(Stats.empty(Instant.now)))
      .append(
        Files[IO].list(path)
          .parEvalMap(8) {file =>
              Files[IO].getBasicFileAttributes(file)
                .map { attributes =>
                  val modified = Instant.ofEpochMilli(attributes.lastModifiedTime.toMillis)
                  Stats(
                    lastSeen = Instant.now(),
                    oldest = Some(modified),
                    newest = Some(modified)
                  )
                }
          }
      )
      .compile
      .foldSemigroup
      .map(_.get)
  }

  def observe(interval: FiniteDuration): Stream[IO, Stats] =
    Stream.fixedRateStartImmediately[IO](interval)
      .evalMap(_ => scan)
}

object DirectoryObserver {
  case class Stats(
                    lastSeen: Instant,
                    oldest: Option[Instant],
                    newest: Option[Instant],
                    count: Long = 1
                  )

  object Stats {
    implicit val semigroup: Semigroup[Stats] = Semigroup.instance { (a, b) =>
      Stats(
        lastSeen = Seq(a.lastSeen, b.lastSeen).max,
        count = a.count + b.count,
        oldest = (a.oldest ++ b.oldest).minOption,
        newest = (a.newest ++ b.newest).maxOption
      )
    }

    def empty(now: Instant): Stats = Stats(
      lastSeen = now,
      oldest = None,
      newest = None,
      count = 0
    )
  }
}
