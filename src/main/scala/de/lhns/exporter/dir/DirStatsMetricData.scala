package de.lhns.exporter.dir

import de.lhns.exporter.dir.DirectoryObserver.DirStats
import fs2.io.file.Path
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.MetricData

class DirStatsMetricData(prefix: String) {
  private val gaugeCount = Gauge(name = s"${prefix}_count")
  private val gaugeCountEmpty = Gauge(name = s"${prefix}_count_empty")
  private val gaugeBytes = Gauge(name = s"${prefix}_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeOldestTs = Gauge(name = s"${prefix}_oldest_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestAge = Gauge(name = s"${prefix}_oldest_age", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestBytes = Gauge(name = s"${prefix}_oldest_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeNewestTs = Gauge(name = s"${prefix}_newest_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestAge = Gauge(name = s"${prefix}_newest_age", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestBytes = Gauge(name = s"${prefix}_newest_bytes", unit = Some(Gauge.unitBytes))

  def toMetricData(dirStats: DirStats, path: Path, tags: Map[String, String]): Seq[MetricData] = {
    val attributes = tags.foldLeft(
      Attributes.builder()
        .put("path", path.toString)
    ) {
      case (builder, (key, value)) => builder.put(key, value)
    }.build()

    Seq(
      gaugeBytes.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, dirStats.size, attributes),
      gaugeCountEmpty.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, dirStats.countEmpty, attributes),
      gaugeCount.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, dirStats.count, attributes)
    ) ++ dirStats.oldest.toSeq.flatMap { oldest =>
      Seq(
        gaugeOldestTs.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, oldest.modified.getEpochSecond, attributes),
        gaugeOldestAge.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, dirStats.collectionEnd.getEpochSecond - oldest.modified.getEpochSecond, attributes),
        gaugeOldestBytes.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, oldest.size, attributes)
      )
    } ++ dirStats.newest.toSeq.flatMap { newest =>
      Seq(
        gaugeNewestTs.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, newest.modified.getEpochSecond, attributes),
        gaugeNewestAge.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, dirStats.collectionEnd.getEpochSecond - newest.modified.getEpochSecond, attributes),
        gaugeNewestBytes.toMetricData(dirStats.collectionStart, dirStats.collectionEnd, newest.size, attributes)
      )
    }
  }
}
