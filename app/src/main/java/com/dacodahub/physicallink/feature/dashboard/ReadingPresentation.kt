package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.dacodahub.physicallink.domain.model.ReadingFreshness

/** Freshness gates presentation, never history ingestion or persisted configuration. */
internal fun readingForDisplay(state: DashboardWidgetState): String? {
    val capability = state.capability ?: return null
    if (state.freshness == ReadingFreshness.UNAVAILABLE || state.timeline?.latestValid == false) return null
    if (!capability.type.equals("number", true)) return state.value?.takeIf { it.isNotBlank() }
    val value = if (state.timeline != null) state.timeline.latestNumeric else state.value?.toDoubleOrNull()
    return value?.takeIf { it.isFinite() }?.let {
        // Retain original decimal precision for Value widgets when it agrees with the timeline.
        state.value?.takeIf { raw -> raw.toDoubleOrNull() == it } ?: it.toString()
    }
}

internal fun displayedFreshness(state: DashboardWidgetState): ReadingFreshness =
    if (readingForDisplay(state) == null) ReadingFreshness.UNAVAILABLE else state.freshness

@Composable
internal fun WidgetReading(state: DashboardWidgetState, controls: WidgetControls? = null) {
    val raw = readingForDisplay(state)
    val stale = raw != null && state.freshness == ReadingFreshness.STALE
    if (stale) Text("Last known", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val unit = if (raw == null) "" else sensorUnit(state.capability?.unit.orEmpty())
    val formatted = formatSensorValue(raw, state.capability?.type ?: "number", state.widget.settings.precision)
    SensorReading(formatted, unit, compact = controls != null,
        numeric = state.capability?.type?.equals("number", true) != false,
        readingDescription = when {
            raw == null -> "Measurement unavailable"
            stale -> "Last known: $formatted $unit".trim()
            else -> null
        })
}
