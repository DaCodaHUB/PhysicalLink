package com.dacodahub.physicallink.domain.model

/** First valid entry wins because live readings are keyed by capability ID. */
fun DeviceCapabilities.validDashboardCapabilities(): List<DeviceCapability> = capabilities
    .filter { it.id.isNotBlank() && it.uuid.isNotBlank() && it.type.isNotBlank() }
    .distinctBy { it.id }

fun defaultDashboard(deviceId: String?, capabilities: DeviceCapabilities?): DashboardLayout {
    if (deviceId.isNullOrBlank() || capabilities == null) return DashboardLayout()
    return DashboardLayout(capabilities.validDashboardCapabilities().map {
        val source = SensorSource(deviceId, it.id)
        DashboardWidget("default:${deviceId.length}:$deviceId:${it.id}", WidgetType.VALUE, source)
    })
}
