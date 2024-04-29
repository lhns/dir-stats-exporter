package de.lhns.exporter.dir

import cats.data.OptionT
import cats.effect.Sync
import cats.effect.std.Env
import cats.syntax.option._
import de.lhns.exporter.dir.Config.DirConfig
import de.lhns.exporter.dir.Config.DirConfig.{TagRule, TagValue}
import fs2.io.file.Path
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.{Duration, FiniteDuration}

case class Config(
                   collectorEndpoint: String,
                   jobName: Option[String],
                   interval: FiniteDuration,
                   adaptiveIntervalMultiplier: Option[Double],
                   directories: Seq[DirConfig],
                   prefix: Option[String]
                 ) {
  val jobNameOrDefault: String = jobName.getOrElse("dir-stats-exporter")

  val prefixOrDefault: String = prefix.getOrElse("dir_stats")
}

object Config {
  implicit val codec: Codec[Config] = deriveCodec

  case class DirConfig(
                        path: Path,
                        recursive: Option[Boolean],
                        interval: Option[FiniteDuration],
                        adaptiveIntervalMultiplier: Option[Double],
                        tags: Option[Map[String, TagValue]],
                        include: Option[Seq[String]],
                        exclude: Option[Seq[String]],
                        includeDirPath: Option[Seq[String]],
                        excludeDirPath: Option[Seq[String]],
                        tagRules: Option[Seq[TagRule]],
                        minDepth: Option[Int],
                        maxDepth: Option[Int],
                        parallelism: Option[Int],
                      ) {
    val recursiveOrDefault: Boolean = recursive.getOrElse(false)

    def intervalOrDefault(config: Config): FiniteDuration =
      interval.getOrElse(config.interval)

    def adaptiveIntervalMultiplierOrDefault(config: Config): Option[Double] =
      adaptiveIntervalMultiplier.orElse(config.adaptiveIntervalMultiplier).filterNot(_ == 0)

    val tagsOrDefault: Map[String, TagValue] = tags.getOrElse(Map.empty)

    val includeOrDefault: Seq[String] = include.orEmpty

    val excludeOrDefault: Seq[String] = exclude.orEmpty

    val includeDirPathOrDefault: Seq[String] = includeDirPath.orEmpty

    val excludeDirPathOrDefault: Seq[String] = excludeDirPath.orEmpty

    val tagRulesOrDefault: Seq[TagRule] = tagRules.getOrElse(Seq.empty)

    def withPath(path: Path): DirConfig = copy(path = path)

    val parallelismOrDefault: Int = parallelism.getOrElse(8)
  }

  object DirConfig {
    implicit val codec: Codec[DirConfig] = deriveCodec

    case class TagValue(value: String)

    object TagValue {
      implicit val codec: Codec[TagValue] = Codec.from(
        Decoder.decodeString
          .or(Decoder.decodeBoolean.map(_.toString))
          .or(Decoder.decodeJsonNumber.map(_.toString))
          .map(TagValue(_)),
        Encoder.encodeString.contramap(_.value)
      )
    }

    case class TagRule(
                        dirPath: Seq[String],
                        tags: Option[Map[String, TagValue]]
                      ) {
      val tagsOrDefault: Map[String, TagValue] = tags.getOrElse(Map.empty)
    }

    object TagRule {
      implicit val codec: Codec[TagRule] = deriveCodec
    }
  }

  private implicit val pathCodec: Codec[Path] = Codec.from(
    Decoder.decodeString.map(Path(_)),
    Encoder.encodeString.contramap(_.toString)
  )

  private implicit val finiteDurationCodec: Codec[FiniteDuration] = Codec.from(
    Decoder.decodeString.map(Duration(_)).map {
      case e: FiniteDuration => e
      case _ => throw new IllegalArgumentException("duration must be finite")
    },
    Encoder.encodeString.contramap(_.toString)
  )

  def fromEnv[F[_] : Sync](env: Env[F]): F[Config] =
    OptionT(env.get("CONFIG"))
      .toRight(new IllegalArgumentException("Missing environment variable: CONFIG"))
      .subflatMap(io.circe.config.parser.decode[Config](_))
      .rethrowT
}
