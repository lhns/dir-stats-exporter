package de.lhns.exporter.dir

import cats.syntax.option._
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.internal.data.{ImmutableDoublePointData, ImmutableGaugeData, ImmutableLongPointData, ImmutableMetricData}
import io.opentelemetry.sdk.resources.Resource

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util

case class Gauge(
                  resource: Resource = Resource.getDefault,
                  instrumentationScopeInfo: InstrumentationScopeInfo = InstrumentationScopeInfo.empty(),
                  name: String,
                  description: Option[String] = None,
                  unit: Option[String] = None
                ) {
  def toMetricData(
                    startTimestamp: Instant,
                    timestamp: Instant,
                    value: Long,
                    attributes: Attributes
                  ): MetricData = ImmutableMetricData.createLongGauge(
    resource,
    instrumentationScopeInfo,
    name,
    description.orEmpty,
    unit.orEmpty,
    ImmutableGaugeData.create(
      util.Arrays.asList(
        ImmutableLongPointData.create(
          ChronoUnit.NANOS.between(Instant.EPOCH, startTimestamp),
          ChronoUnit.NANOS.between(Instant.EPOCH, timestamp),
          attributes,
          value
        )
      )
    )
  )

  def toMetricData(
                    startTimestamp: Instant,
                    timestamp: Instant,
                    value: Double,
                    attributes: Attributes
                  ): MetricData = ImmutableMetricData.createDoubleGauge(
    resource,
    instrumentationScopeInfo,
    name,
    description.orEmpty,
    unit.orEmpty,
    ImmutableGaugeData.create(
      util.Arrays.asList(
        ImmutableDoublePointData.create(
          ChronoUnit.NANOS.between(Instant.EPOCH, startTimestamp),
          ChronoUnit.NANOS.between(Instant.EPOCH, timestamp),
          attributes,
          value
        )
      )
    )
  )
}

object Gauge {
  val unitSeconds = "seconds"
  val unitBytes = "bytes"
}
