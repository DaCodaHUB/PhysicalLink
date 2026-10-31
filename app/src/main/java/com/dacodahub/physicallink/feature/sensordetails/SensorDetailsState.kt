package com.dacodahub.physicallink.feature.sensordetails

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import java.math.BigDecimal
import java.math.MathContext

data class SensorDetailsTarget(val source: SensorSource, val widgetId: String)

internal data class SensorDetailsInput(
    val deviceId: String? = null, val metadata: DeviceCapabilities? = null,
    val history: SensorHistorySnapshot = SensorHistorySnapshot(),
    val connection: ConnectionState = ConnectionState.Disconnected,
    val values: Map<String, String> = emptyMap(), val live: LiveSensorState = LiveSensorState(),
    val manifest: SensorManifest? = null, val nowMs: Long = 0, val simulated: Boolean = false
)
internal data class HistoryStatistics(val minimum: Double, val average: BigDecimal, val maximum: Double,
    val count: Int, val firstMs: Long, val lastMs: Long)

/** Raw retained samples, not the chart's reduced geometry. No synthetic points or ranges. */
internal fun historyStatistics(samples: List<NumericSample>, nowMs: Long, windowSeconds: Int): HistoryStatistics? {
    require(windowSeconds in listOf(60, 300, 900))
    val valid = samples.filter { it.value.isFinite() && it.timeMs in (nowMs - windowSeconds * 1000L)..nowMs }
    if (valid.isEmpty()) return null
    val sum = valid.fold(BigDecimal.ZERO) { total, point -> total + BigDecimal.valueOf(point.value) }
    return HistoryStatistics(valid.minOf { it.value }, sum.divide(BigDecimal.valueOf(valid.size.toLong()), MathContext.DECIMAL128),
        valid.maxOf { it.value }, valid.size, valid.minOf { it.timeMs }, valid.maxOf { it.timeMs })
}
internal data class SensorDetailsState(
    val source: SensorSource, val reading: DashboardWidgetState, val deviceName: String?,
    val status: String, val acquisitionAgeMs: Long?, val statistics: HistoryStatistics?, val windowSeconds: Int,
    val sessionId: Long, val missing: Boolean, val widgetMissing: Boolean = false, val loadingWidget: Boolean = false
)

/** A route resolves against the current device metadata and the repository's current session only. */
internal fun sensorDetailsState(source: SensorSource, input: SensorDetailsInput, windowSeconds: Int? = null, configuredWidget: DashboardWidget? = null, widgetMissing: Boolean = false): SensorDetailsState {
    val window = windowSeconds ?: configuredWidget?.settings?.chartWindowSeconds ?: 300
    val staleSeconds = configuredWidget?.settings?.staleAfterSeconds ?: 30
    val sameDevice = source.deviceId == input.deviceId && source.deviceId == input.history.deviceId
    val capability = input.metadata?.validDashboardCapabilities()?.firstOrNull { it.id == source.capabilityId }
        ?.takeIf { sameDevice && (if (input.metadata.version == 2) input.manifest?.dashboardDeviceId() == source.deviceId else true) }
    val retained = input.history.sensors[source].takeIf { capability != null }
    val connected = sameDevice && input.connection == ConnectionState.Connected && input.history.active
    val live = input.live.readings[source.capabilityId]?.takeIf {
        connected && capability != null && (input.metadata?.version != 2 || input.live.synchronized) &&
            (it.value == null || retained?.lastSuccessfulMs == null || it.receivedAtMs - it.ageMs >= retained.lastSuccessfulMs)
    }
    // The validated live record may be newer than the published 4Hz history snapshot.
    // Update only the reading header; do not manufacture a history sample or change statistics.
    val timeline = if (live != null) (retained ?: SensorTimeline()).copy(
        lastSuccessfulMs = if (live.value?.isFinite() == true) live.receivedAtMs - live.ageMs else retained?.lastSuccessfulMs,
        latestNumeric = live.value?.takeIf { it.isFinite() }, latestValid = live.value?.isFinite() == true) else retained
    val reportedAge = live?.takeIf { it.value?.isFinite() == true }?.acquisitionAgeMs(input.nowMs)
    val fresh = when {
        live != null && live.value?.isFinite() != true -> ReadingFreshness.UNAVAILABLE
        else -> readingFreshness(timeline, connected && capability != null, input.nowMs, staleSeconds).let {
            if (it != ReadingFreshness.UNAVAILABLE && (reportedAge ?: timeline?.lastSuccessfulMs?.let { updated -> input.nowMs - updated } ?: 0) >= staleSeconds * 1000L)
                ReadingFreshness.STALE else it
        }
    }
    val widget = configuredWidget?.copy(settings = configuredWidget.settings.copy(chartWindowSeconds = window)) ?: DashboardWidget("sensor-details", WidgetType.CHART, source,
        settings = WidgetSettings(chartWindowSeconds = window, precision = 3, staleAfterSeconds = 30))
    val reading = DashboardWidgetState(widget, capability, input.values[source.capabilityId], timeline, fresh, input.nowMs, input.simulated)
    val status = when {
        !connected -> "Disconnected"
        capability == null -> "Sensor unavailable"
        displayedFreshness(reading) == ReadingFreshness.UNAVAILABLE -> "Unavailable"
        fresh == ReadingFreshness.STALE -> "Stale - last known reading"
        else -> "Live"
    }
    val age = if (connected && capability != null && timeline?.latestValid == true && fresh != ReadingFreshness.UNAVAILABLE)
        reportedAge
            ?: timeline.lastSuccessfulMs?.takeIf { it <= input.nowMs }?.let { input.nowMs - it }
        else null
    return SensorDetailsState(source, reading, input.metadata?.device?.takeIf { sameDevice }, status, age,
        if (widget.type == WidgetType.CHART) timeline?.let { historyStatistics(it.samples, input.nowMs, window) } else null, window, input.history.sessionId, capability == null, widgetMissing)
}

/** Associates metadata epochs with a session token, never retaining another copy of samples. */
internal class SensorDetailsSessionGate {
    private var binding: Pair<String?, String?>? = null
    private var session: Long? = null
    fun accept(input: SensorDetailsInput): SensorDetailsInput {
        if (input.connection != ConnectionState.Connected || !input.history.active || input.deviceId != input.history.deviceId ||
            (input.metadata?.version == 2 && input.manifest == null)) return input
        val key = input.deviceId to input.manifest?.epoch
        if (binding != null && binding != key && session == input.history.sessionId) {
            // Metadata has moved to another epoch before the session history reset arrives.
            return input.copy(history = input.history.copy(sensors = emptyMap()), values = emptyMap(), live = LiveSensorState())
        }
        binding = key
        session = input.history.sessionId
        return input
    }
}