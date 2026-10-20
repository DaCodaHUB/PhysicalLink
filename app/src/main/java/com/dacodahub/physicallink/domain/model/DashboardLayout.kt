package com.dacodahub.physicallink.domain.model

/** Sensor identity is independent of display labels and widget instance identity. */
data class SensorSource(val deviceId: String, val capabilityId: String)

enum class WidgetType { VALUE, GAUGE, CHART, RANGE }

data class DashboardWidget(
    val id: String,
    val type: WidgetType,
    val source: SensorSource,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val hidden: Boolean = false,
    val settings: WidgetSettings = WidgetSettings()
)

/** List order defines placement until explicit positioning is introduced. */
data class DashboardLayout(val widgets: List<DashboardWidget> = emptyList())
