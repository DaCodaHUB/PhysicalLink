package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.dacodahub.physicallink.core.designsystem.theme.LocalWidgetAccent
import com.dacodahub.physicallink.core.designsystem.theme.widgetAccent
import com.dacodahub.physicallink.domain.model.*

internal fun freshnessLabel(state: DashboardWidgetState): String = (if (state.simulated) "Simulated - " else "") + when (displayedFreshness(state)) {
    ReadingFreshness.LIVE -> "Live"
    ReadingFreshness.STALE -> "Stale - last update ${((state.nowMs - (state.timeline?.lastSuccessfulMs ?: state.nowMs)) / 1000).coerceAtLeast(0)}s ago"
    ReadingFreshness.UNAVAILABLE -> if (state.timeline == null) "Unavailable - no reading yet" else "Unavailable"
}

@Composable
internal fun DashboardWidgetCard(state: DashboardWidgetState, controls: WidgetControls? = null) {
    CompositionLocalProvider(LocalWidgetAccent provides widgetAccent(state.widget.source.capabilityId)) {
    val title = (state.capability?.name ?: sensorName(state.widget.source.capabilityId))
    if (state.capability == null) {
        WidgetContainer(title, status = "This sensor is not currently available.", statusIsAlert = true, controls = controls) {
            Text("--", style = MaterialTheme.typography.headlineLarge)
            Text("Your widget is saved. Reconnect a project that provides this sensor.", style = MaterialTheme.typography.bodyMedium)
        }
    } else when (state.widget.type) {
        WidgetType.VALUE -> WidgetContainer(title, status = freshnessLabel(state), controls = controls,
            statusIsAlert = displayedFreshness(state) != ReadingFreshness.LIVE) { WidgetReading(state, controls) }
        WidgetType.CHART -> ChartWidget(state, controls)
        WidgetType.GAUGE, WidgetType.RANGE -> BoundedWidget(state, controls)
    }
}
}
