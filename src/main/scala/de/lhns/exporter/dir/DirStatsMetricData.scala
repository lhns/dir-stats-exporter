package de.lhns.exporter.dir

import de.lhns.exporter.dir.DirectoryObserver.DirStatsCollection
import fs2.io.file.Path
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.MetricData

class DirStatsMetricData(prefix: String) {
  private val gaugeCount = Gauge(name = s"${prefix}_count")
  private val gaugeBytes = Gauge(name = s"${prefix}_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeOldestTs = Gauge(name = s"${prefix}_oldest_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestAge = Gauge(name = s"${prefix}_oldest_age", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestBytes = Gauge(name = s"${prefix}_oldest_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeNewestTs = Gauge(name = s"${prefix}_newest_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestAge = Gauge(name = s"${prefix}_newest_age", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestBytes = Gauge(name = s"${prefix}_newest_bytes", unit = Some(Gauge.unitBytes))

  def toMetricData(dirStatsCollection: DirStatsCollection, path: Path, tags: Map[String, String]): Iterable[MetricData] = {
    val startTimestamp = dirStatsCollection.collectionStart
    val timestamp = dirStatsCollection.collectionEnd

    dirStatsCollection.groups.flatMap {
      case (key, dirStats) =>
        val attributes = tags.foldLeft(
          Attributes.builder()
            .put("path", path.toString)
            .put("empty", key.empty.toString)
            .put("hidden", key.hidden.toString)
        ) {
          case (builder, (key, value)) => builder.put(key, value)
        }.build()

        Seq(
          gaugeBytes.toMetricData(startTimestamp, timestamp, dirStats.size, attributes),
          gaugeCount.toMetricData(startTimestamp, timestamp, dirStats.count, attributes)
        ) ++ dirStats.oldest.toSeq.flatMap { oldest =>
          Seq(
            gaugeOldestTs.toMetricData(startTimestamp, timestamp, oldest.modified.getEpochSecond, attributes),
            gaugeOldestAge.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - oldest.modified.getEpochSecond, attributes),
            gaugeOldestBytes.toMetricData(startTimestamp, timestamp, oldest.size, attributes)
          )
        } ++ dirStats.newest.toSeq.flatMap { newest =>
          Seq(
            gaugeNewestTs.toMetricData(startTimestamp, timestamp, newest.modified.getEpochSecond, attributes),
            gaugeNewestAge.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - newest.modified.getEpochSecond, attributes),
            gaugeNewestBytes.toMetricData(startTimestamp, timestamp, newest.size, attributes)
          )
        }
    }
  }
}
