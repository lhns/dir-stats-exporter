package de.lhns.exporter.dir

import de.lhns.exporter.dir.Config.DirConfig
import fs2.io.file.Path
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}

import scala.concurrent.duration.{Duration, FiniteDuration}

case class Config(
                   endpoint: String,
                   interval: FiniteDuration,
                   adaptiveIntervalMultiplier: Option[Double],
                   directories: Seq[DirConfig],
                   prefix: Option[String]
                 ) {
  val prefixOrDefault: String = prefix.getOrElse("dir_stats")
}

object Config {
  implicit val codec: Codec[Config] = deriveCodec

  case class DirConfig(
                        path: Path,
                        //TODO: recursive: Boolean = false,
                        interval: Option[FiniteDuration],
                        adaptiveIntervalMultiplier: Option[Double],
                        tags: Option[Map[String, String]],
                        filter: Option[String]
                      ) {
    val tagsOrDefault: Map[String, String] = tags.getOrElse(Map.empty)

    def intervalOrDefault(config: Config): FiniteDuration =
      interval.getOrElse(config.interval)

    def adaptiveIntervalMultiplierOrDefault(config: Config): Option[Double] =
      adaptiveIntervalMultiplier.orElse(config.adaptiveIntervalMultiplier)
  }

  object DirConfig {
    implicit val codec: Codec[DirConfig] = deriveCodec
  }

  private implicit val pathCodec: Codec[Path] = Codec.from(
    Decoder.decodeString.map(Path(_)),
    Encoder.encodeString.contramap(_.toString)
  )

  private implicit val finiteDurationCodec: Codec[FiniteDuration] = Codec.from(
    Decoder.decodeString.map(Duration(_)).map { case e: FiniteDuration => e },
    Encoder.encodeString.contramap(_.toString)
  )

  lazy val fromEnv: Config =
    io.circe.config.parser.decode[Config](
      Option(System.getenv("CONFIG"))
        .getOrElse(throw new IllegalArgumentException("Missing environment variable: CONFIG"))
    ).toTry.get
}
