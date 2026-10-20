package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*

data class DashboardWidgetState(
    val widget: DashboardWidget,
    val capability: DeviceCapability?,
    val value: String?
)

data class DashboardState(
    val deviceName: String? = null,
    val discovering: Boolean = true,
    val widgets: List<DashboardWidgetState> = emptyList(),
    val hiddenWidgets: List<DashboardWidgetState> = emptyList(),
    val availableSensors: List<SensorSource> = emptyList(),
    val isEditing: Boolean = false,
    val loadingLayout: Boolean = false,
    val persistenceError: String? = null
)

/** Null configuration follows discovery; explicit configurations preserve missing sources. */
fun dashboardState(
    deviceId: String?,
    capabilities: DeviceCapabilities?,
    values: Map<String, String>,
    configuration: DashboardLayout?
): DashboardState {
    val layout = configuration ?: defaultDashboard(deviceId, capabilities)
    val sensors = capabilities?.validDashboardCapabilities().orEmpty().associateBy { it.id }
    val widgets = layout.widgets.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { widget ->
        val capability = sensors[widget.source.capabilityId]
            .takeIf { !deviceId.isNullOrBlank() && widget.source.deviceId == deviceId }
        DashboardWidgetState(widget, capability, capability?.let { values[it.id] })
    }
    return DashboardState(
        deviceName = capabilities?.device,
        discovering = capabilities == null,
        widgets = widgets.filterNot { it.widget.hidden },
        hiddenWidgets = widgets.filter { it.widget.hidden },
        availableSensors = if (deviceId.isNullOrBlank()) emptyList()
            else sensors.keys.map { SensorSource(deviceId, it) }
    )
}
