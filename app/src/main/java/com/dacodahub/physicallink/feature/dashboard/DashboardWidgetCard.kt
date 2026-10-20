package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.dacodahub.physicallink.domain.model.WidgetType

@Composable
internal fun DashboardWidgetCard(state: DashboardWidgetState, controls: WidgetControls? = null) {
    val title = sensorName(state.widget.source.capabilityId)
    when (state.widget.type) {
        WidgetType.VALUE -> if (state.capability != null) SensorCard(state.capability, state.value, controls)
            else WidgetContainer(title, status = "This sensor is not currently available.", controls = controls) {
                Text("--", style = MaterialTheme.typography.headlineLarge)
                Text("Your widget is saved. Reconnect a project that provides this sensor.", style = MaterialTheme.typography.bodyMedium)
            }
        WidgetType.GAUGE -> UnavailableVisualization(title, WidgetPresentation.GAUGE, controls)
        WidgetType.CHART -> UnavailableVisualization(title, WidgetPresentation.CHART, controls)
    }
}
