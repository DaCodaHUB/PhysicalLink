package com.dacodahub.physicallink.domain.model

/** Retain missing/hidden/duplicate instances; append only genuinely new sensor sources. */
fun DashboardLayout.withDiscoveredSensors(deviceId: String, capabilities: DeviceCapabilities): DashboardLayout {
    val sources = widgets.map { it.source }.toSet()
    val ids = widgets.map { it.id }.toMutableSet()
    val additions = defaultDashboard(deviceId, capabilities).widgets.filter { it.source !in sources }.map { widget ->
        var id = widget.id
        var suffix = 1
        while (!ids.add(id)) { id = "${widget.id}#${suffix++}" }
        widget.copy(id = id)
    }
    return if (additions.isEmpty()) this else copy(widgets = widgets + additions)
}
