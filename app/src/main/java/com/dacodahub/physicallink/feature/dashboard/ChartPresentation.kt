package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import java.util.Locale

/** Plot time changes at most 4Hz for unchanged history; never exclude its latest real sample. */
internal fun chartRenderTime(nowMs: Long, samples: List<NumericSample>): Long =
    maxOf(nowMs / 250 * 250, minOf(nowMs, samples.lastOrNull()?.timeMs ?: 0))

internal fun chartCurrentValue(state: DashboardWidgetState): String {
    val value = readingForDisplay(state)?.toDoubleOrNull()
    return if (value == null || !value.isFinite()) "Current value unavailable"
    else "${if (displayedFreshness(state) == ReadingFreshness.STALE) "Last known" else "Current"}: ${formatSensorValue(value.toString(), "number", state.widget.settings.precision)} ${sensorUnit(state.capability?.unit.orEmpty())}".trim()
}

/** Axis endpoints remain distinct even when the chosen reading precision rounds both to zero. */
internal fun chartAxisLabels(series: ChartSeries, precision: Int): Pair<String, String> {
    val low = formatSensorValue(series.minimum.toString(), "number", precision)
    val high = formatSensorValue(series.maximum.toString(), "number", precision)
    if (low != high) return low to high
    val compact = String.format(Locale.getDefault(), "%.3g", series.minimum) to String.format(Locale.getDefault(), "%.3g", series.maximum)
    return if (compact.first != compact.second) compact else series.minimum.toString() to series.maximum.toString()
}

internal fun chartSummary(state: DashboardWidgetState, series: ChartSeries): String {
    val axes = chartAxisLabels(series, state.widget.settings.precision)
    val segment = series.segments.last()
    val first = segment.first(); val last = segment.last()
    fun number(value: Double) = formatSensorValue(value.toString(), "number", state.widget.settings.precision)
    val unit = sensorUnit(state.capability?.unit.orEmpty())
    val trend = if (segment.size < 2 || last.timeMs <= first.timeMs) "Trend unavailable: one sample in the latest segment."
        else "Latest recorded segment ${when { last.value > first.value -> "rose"; last.value < first.value -> "fell"; else -> "was unchanged" }} from ${number(first.value)} to ${number(last.value)} $unit over ${(last.timeMs - first.timeMs) / 1000} seconds."
    return "${(state.capability?.name ?: sensorName(state.widget.source.capabilityId))}. Line chart, ${series.count} samples in the last ${state.widget.settings.chartWindowSeconds} seconds. Value axis ${axes.first} to ${axes.second} $unit. ${chartCurrentValue(state)}. ${freshnessLabel(state)}. $trend Gaps indicate missing updates; trend does not cross gaps. Values outside custom axes are clipped without changing recorded samples."
}

/** Receive-time coordinates: new samples enter at the right; age moves them left. */
internal fun chartTimeFraction(sampleMs: Long, nowMs: Long, windowSeconds: Int): Float {
    require(windowSeconds > 0)
    return (1.0 - (nowMs.toDouble() - sampleMs.toDouble()) / (windowSeconds * 1000.0))
        .coerceIn(0.0, 1.0).toFloat()
}
