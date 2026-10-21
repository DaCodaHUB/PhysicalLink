package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import java.util.Locale

/** Plot time changes at most 4Hz for unchanged history; never exclude its latest real sample. */
internal fun chartRenderTime(nowMs: Long, samples: List<NumericSample>): Long =
    maxOf(nowMs / 250 * 250, minOf(nowMs, samples.lastOrNull()?.timeMs ?: 0))

internal fun chartCurrentValue(state: DashboardWidgetState): String {
    val value = state.timeline?.takeIf { it.latestValid }?.latestNumeric
    return if (value == null || !value.isFinite()) "Current value unavailable"
    else "Current: ${formatSensorValue(value.toString(), "number", state.widget.settings.precision)} ${sensorUnit(state.capability?.unit.orEmpty())}".trim()
}

/** Axis endpoints remain distinct even when the chosen reading precision rounds both to zero. */
internal fun chartAxisLabels(series: ChartSeries, precision: Int): Pair<String, String> {
    val low = formatSensorValue(series.minimum.toString(), "number", precision)
    val high = formatSensorValue(series.maximum.toString(), "number", precision)
    return if (low != high) low to high else String.format(Locale.getDefault(), "%.3g", series.minimum) to
        String.format(Locale.getDefault(), "%.3g", series.maximum)
}

internal fun chartSummary(state: DashboardWidgetState, series: ChartSeries): String {
    val axes = chartAxisLabels(series, state.widget.settings.precision)
    val segment = series.segments.last()
    val first = segment.first(); val last = segment.last()
    fun number(value: Double) = formatSensorValue(value.toString(), "number", state.widget.settings.precision)
    val unit = sensorUnit(state.capability?.unit.orEmpty())
    val trend = if (segment.size < 2 || last.timeMs <= first.timeMs) "Trend unavailable: one sample in the latest segment."
        else "Latest recorded segment ${when { last.value > first.value -> "rose"; last.value < first.value -> "fell"; else -> "was unchanged" }} from ${number(first.value)} to ${number(last.value)} $unit over ${(last.timeMs - first.timeMs) / 1000} seconds."
    return "${sensorName(state.widget.source.capabilityId)}. Line chart, ${series.count} samples in the last ${state.widget.settings.chartWindowSeconds} seconds. Value axis ${axes.first} to ${axes.second} $unit. ${chartCurrentValue(state)}. ${freshnessLabel(state)}. $trend Gaps indicate missing updates; trend does not cross gaps."
}
