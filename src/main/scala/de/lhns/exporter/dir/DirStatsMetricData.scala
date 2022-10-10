package de.lhns.exporter.dir

import de.lhns.exporter.dir.DirectoryObserver.DirStatsCollection
import fs2.io.file.Path
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME

class DirStatsMetricData(jobName: String, prefix: String) {
  private val resource = Resource.getDefault.merge(Resource.create(Attributes.of(SERVICE_NAME, jobName)))

  private val gaugeDurationMs = Gauge(resource, name = s"${prefix}_duration_ms", unit = Some(Gauge.unitMilliseconds))
  private val gaugeTs = Gauge(resource, name = s"${prefix}_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeAge = Gauge(resource, name = s"${prefix}_age", unit = Some(Gauge.unitSeconds))
  private val gaugeCount = Gauge(resource, name = s"${prefix}_count")
  private val gaugeBytes = Gauge(resource, name = s"${prefix}_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeOldestTs = Gauge(resource, name = s"${prefix}_oldest_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestAge = Gauge(resource, name = s"${prefix}_oldest_age", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestBytes = Gauge(resource, name = s"${prefix}_oldest_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeNewestTs = Gauge(resource, name = s"${prefix}_newest_ts", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestAge = Gauge(resource, name = s"${prefix}_newest_age", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestBytes = Gauge(resource, name = s"${prefix}_newest_bytes", unit = Some(Gauge.unitBytes))

  def toMetricData(dirStatsCollection: DirStatsCollection, path: Path, tags: Map[String, String]): Seq[MetricData] = {
    val startTimestamp = dirStatsCollection.collectionStart
    val timestamp = dirStatsCollection.collectionEnd
    val durationMs = timestamp.toEpochMilli - startTimestamp.toEpochMilli
    val attributes = tags.foldLeft(
      Attributes.builder()
        .put("path", path.toString)
    ) {
      case (builder, (key, value)) => builder.put(key, value)
    }.build()

    Seq(
      gaugeDurationMs.toMetricData(startTimestamp, timestamp, durationMs, attributes),
      gaugeTs.toMetricData(startTimestamp, timestamp, dirStatsCollection.modified.getEpochSecond, attributes),
      gaugeAge.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - dirStatsCollection.modified.getEpochSecond, attributes)
    ) ++ dirStatsCollection.groups.flatMap {
      case (key, dirStats) =>
        val groupAttributes = attributes.toBuilder
          .put("empty", key.empty.toString)
          .put("hidden", key.hidden.toString)
          .build()

        Seq(
          gaugeBytes.toMetricData(startTimestamp, timestamp, dirStats.size, groupAttributes),
          gaugeCount.toMetricData(startTimestamp, timestamp, dirStats.count, groupAttributes)
        ) ++ dirStats.oldest.toSeq.flatMap { oldest =>
          val fileAttributes = groupAttributes.toBuilder.put("name", oldest.name).build()
          Seq(
            gaugeOldestTs.toMetricData(startTimestamp, timestamp, oldest.modified.getEpochSecond, fileAttributes),
            gaugeOldestAge.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - oldest.modified.getEpochSecond, fileAttributes),
            gaugeOldestBytes.toMetricData(startTimestamp, timestamp, oldest.size, fileAttributes)
          )
        } ++ dirStats.newest.toSeq.flatMap { newest =>
          val fileAttributes = groupAttributes.toBuilder.put("name", newest.name).build()
          Seq(
            gaugeNewestTs.toMetricData(startTimestamp, timestamp, newest.modified.getEpochSecond, fileAttributes),
            gaugeNewestAge.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - newest.modified.getEpochSecond, fileAttributes),
            gaugeNewestBytes.toMetricData(startTimestamp, timestamp, newest.size, fileAttributes)
          )
        }
    }
  }
}
