package de.lhns.exporter.dir

import de.lhns.exporter.dir.Config.DirConfig.TagValue
import io.circe.{Codec, Decoder, Encoder}

trait TagFormat {
  def transform(kv: (String, TagValue)): List[(String, TagValue)]
}

object TagFormat {
  object Default extends TagFormat {
    override def transform(kv: (String, TagValue)): List[(String, TagValue)] = List(kv)
  }

  object Duration extends TagFormat {
    override def transform(kv: (String, TagValue)): List[(String, TagValue)] = List(
      kv,
      (kv._1 + "_duration", TagValue(scala.concurrent.duration.Duration(kv._2.value).toSeconds.toString))
    )
  }

  implicit val codec: Codec[TagFormat] = Codec.from(
    Decoder[String].map {
      case "duration" => Duration
      case _ => Default
    },
    Encoder[String].contramap {
      case Duration => "duration"
      case _ => ""
    }
  )
}
