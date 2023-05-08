package de.lhns.exporter.dir

import de.lhns.exporter.dir.DirectoryObserver.DirStatsCollection
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME

import java.time.temporal.ChronoUnit

class DirStatsMetricData(jobName: String, prefix: String) {
  private val resource = Resource.getDefault.merge(Resource.create(Attributes.of(SERVICE_NAME, jobName)))

  private val gaugeDurationSeconds = Gauge(resource, name = s"${prefix}_duration_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeTimestampSeconds = Gauge(resource, name = s"${prefix}_timestamp_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeAgeSeconds = Gauge(resource, name = s"${prefix}_age_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeCount = Gauge(resource, name = s"${prefix}_count")
  private val gaugeBytes = Gauge(resource, name = s"${prefix}_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeOldestTimestampSeconds = Gauge(resource, name = s"${prefix}_oldest_timestamp_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestAgeSeconds = Gauge(resource, name = s"${prefix}_oldest_age_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeOldestBytes = Gauge(resource, name = s"${prefix}_oldest_bytes", unit = Some(Gauge.unitBytes))
  private val gaugeNewestTimestampSeconds = Gauge(resource, name = s"${prefix}_newest_timestamp_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestAgeSeconds = Gauge(resource, name = s"${prefix}_newest_age_seconds", unit = Some(Gauge.unitSeconds))
  private val gaugeNewestBytes = Gauge(resource, name = s"${prefix}_newest_bytes", unit = Some(Gauge.unitBytes))

  def toMetricData(dirStatsCollection: DirStatsCollection): Seq[MetricData] = {
    val startTimestamp = dirStatsCollection.collectionStart
    val timestamp = dirStatsCollection.collectionEnd

    val attributes = dirStatsCollection.dirConfig.tagsOrDefault.foldLeft(
      Attributes.builder()
    ) { case (builder, (key, value)) =>
      builder.put(key, value)
    }.build()

    Seq(
      gaugeDurationSeconds.toMetricData(startTimestamp, timestamp, ChronoUnit.MILLIS.between(startTimestamp, timestamp) / 1000d, attributes),
      gaugeTimestampSeconds.toMetricData(startTimestamp, timestamp, dirStatsCollection.modified.getEpochSecond, attributes),
      gaugeAgeSeconds.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - dirStatsCollection.modified.getEpochSecond, attributes)
    ) ++ dirStatsCollection.groups.flatMap {
      case (key, dirStats) =>
        val groupAttributes = attributes.toBuilder
          .put("path", key.path.toString)
          .put("empty", key.empty.toString)
          .put("hidden", key.hidden.toString)
          .build()

        Seq(
          gaugeBytes.toMetricData(startTimestamp, timestamp, dirStats.size, groupAttributes),
          gaugeCount.toMetricData(startTimestamp, timestamp, dirStats.count, groupAttributes)
        ) ++ dirStats.oldest.toSeq.flatMap { oldest =>
          val fileAttributes = groupAttributes.toBuilder.put("name", oldest.name).build()
          Seq(
            gaugeOldestTimestampSeconds.toMetricData(startTimestamp, timestamp, oldest.modified.getEpochSecond, fileAttributes),
            gaugeOldestAgeSeconds.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - oldest.modified.getEpochSecond, fileAttributes),
            gaugeOldestBytes.toMetricData(startTimestamp, timestamp, oldest.size, fileAttributes)
          )
        } ++ dirStats.newest.toSeq.flatMap { newest =>
          val fileAttributes = groupAttributes.toBuilder.put("name", newest.name).build()
          Seq(
            gaugeNewestTimestampSeconds.toMetricData(startTimestamp, timestamp, newest.modified.getEpochSecond, fileAttributes),
            gaugeNewestAgeSeconds.toMetricData(startTimestamp, timestamp, timestamp.getEpochSecond - newest.modified.getEpochSecond, fileAttributes),
            gaugeNewestBytes.toMetricData(startTimestamp, timestamp, newest.size, fileAttributes)
          )
        }
    }
  }
}
