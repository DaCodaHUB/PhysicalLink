package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.dacodahub.physicallink.domain.model.*

internal fun freshnessLabel(state: DashboardWidgetState): String = (if (state.simulated) "Simulated - " else "") + when (state.freshness) {
    ReadingFreshness.LIVE -> "Live"
    ReadingFreshness.STALE -> "Stale - last update ${((state.nowMs - (state.timeline?.lastSuccessfulMs ?: state.nowMs)) / 1000).coerceAtLeast(0)}s ago"
    ReadingFreshness.UNAVAILABLE -> if (state.timeline == null) "Unavailable - no update received this session" else "Unavailable - disconnected or invalid reading"
}

@Composable
internal fun DashboardWidgetCard(state: DashboardWidgetState, controls: WidgetControls? = null) {
    val title = (state.capability?.name ?: sensorName(state.widget.source.capabilityId))
    if (state.capability == null) {
        WidgetContainer(title, status = "This sensor is not currently available.", controls = controls) {
            Text("--", style = MaterialTheme.typography.headlineLarge)
            Text("Your widget is saved. Reconnect a project that provides this sensor.", style = MaterialTheme.typography.bodyMedium)
        }
    } else when (state.widget.type) {
        WidgetType.VALUE -> SensorCard(state.capability, state.value, controls, state.widget.settings.precision, freshnessLabel(state))
        WidgetType.CHART -> ChartWidget(state, controls)
        WidgetType.GAUGE, WidgetType.RANGE -> BoundedWidget(state, controls)
    }
}
