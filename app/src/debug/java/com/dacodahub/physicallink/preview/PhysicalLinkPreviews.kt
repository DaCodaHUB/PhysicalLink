package com.dacodahub.physicallink.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.feature.devices.DevicesContent

// Metadata matches https://github.com/DaCodaHUB/PhysicalLink-firmware/blob/main/examples/real_sensors/main.cpp. All sensor readings stay null.
private val capabilities = DeviceCapabilities(2, "PhysicalLink", listOf(
    DeviceCapability("soil", "v2:soil", "raw", "number"),
    DeviceCapability("temperature", "v2:temperature", "C", "number"),
    DeviceCapability("humidity", "v2:humidity", "%", "number"),
    DeviceCapability("pressure", "v2:pressure", "hPa", "number")
))
private val deviceId = "preview-only"

@Preview(name = "Devices - empty", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesEmptyPreview() {
    PhysicalLinkTheme(dynamicColor = false) { DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle) }
}
@Preview(name = "Devices - scanning", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesScanningPreview() {
    PhysicalLinkTheme(dynamicColor = false) { DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Scanning) }
}
@Preview(name = "Devices - connecting", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesConnectingPreview() {
    PhysicalLinkTheme(dynamicColor = false) { DevicesContent(emptyList(), ConnectionState.Connecting, ScanState.Idle, deviceId) }
}
@Preview(name = "Devices - ready", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesConnectedPreview() {
    PhysicalLinkTheme(dynamicColor = false) { DevicesContent(emptyList(), ConnectionState.Connected, ScanState.Idle, deviceId, capabilities) }
}
@Preview(name = "Devices - error", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesErrorPreview() {
    PhysicalLinkTheme(dynamicColor = false) { DevicesContent(emptyList(), ConnectionState.Disconnected,
        ScanState.Error("Bluetooth is disabled. Turn it on, then scan again.")) }
}
@Preview(name = "Dashboard - light, waiting for readings", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "Dashboard - dark, waiting for readings", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dashboard - large text", showBackground = true, widthDp = 360, heightDp = 960, fontScale = 1.6f)
@Composable private fun DashboardPreview() {
    PhysicalLinkTheme(dynamicColor = false) {
        DashboardContent(ConnectionState.Connected, dashboardState(deviceId, capabilities, emptyMap(), null), deviceId, capabilities)
    }
}
@Preview(name = "Dashboard - edit, tablet", showBackground = true, widthDp = 840, heightDp = 1000)
@Composable private fun DashboardEditingPreview() {
    val layout = defaultDashboard(deviceId, capabilities).let {
        it.copy(widgets = it.widgets.mapIndexed { index, widget -> widget.copy(columnSpan = if (index == 2) 2 else 1, hidden = index == 3) })
    }
    PhysicalLinkTheme(dynamicColor = false) {
        DashboardContent(ConnectionState.Connected, dashboardState(deviceId, capabilities, emptyMap(), layout).copy(isEditing = true), deviceId, capabilities)
    }
}
@Preview(name = "Visualizations - real empty states", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun VisualizationsPreview() {
    val source = SensorSource(deviceId, "soil")
    val layout = DashboardLayout(WidgetType.entries.map { type ->
        DashboardWidget(type.name, type, source, columnSpan = 2)
    })
    PhysicalLinkTheme(dynamicColor = false) {
        DashboardContent(ConnectionState.Connected, dashboardState(deviceId, capabilities, emptyMap(), layout), deviceId, capabilities)
    }
}

// Advertisement fixtures below are preview-only, not observations from real hardware.
@Preview(name = "Devices - compact rows", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesFoundPreview() {
    PhysicalLinkTheme(dynamicColor = false) {
        DevicesContent(listOf(DiscoveredDevice("PhysicalLink", "preview-only", -58)),
            ConnectionState.Disconnected, ScanState.Idle)
    }
}

@Preview(name = "Widget configuration - numeric visualizations", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun WidgetConfigurationPreview() {
    PhysicalLinkTheme(dynamicColor = false) {
        val widget = dashboardState(deviceId, capabilities, emptyMap(), null).widgets.first()
        WidgetConfigurationSheet(widget, capabilities.device, 0, 4, DashboardActions(), onDismiss = {})
    }
}

@Preview(name = "Demo Mode - waiting", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "Demo Mode - dark", showBackground = true, widthDp = 393, heightDp = 852,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun DemoDashboardPreview() {
    val metadata = com.dacodahub.physicallink.data.repository.DemoDeviceRepository.CAPABILITIES
    val identity = com.dacodahub.physicallink.data.repository.DemoDeviceRepository.DEVICE_ID
    val state = dashboardState(identity, metadata, emptyMap(), null)
    PhysicalLinkTheme(dynamicColor = false) {
        DashboardContent(ConnectionState.Connected, state.copy(widgets = state.widgets.map { it.copy(simulated = true) }),
            identity, metadata, isDemo = true)
    }
}

@Preview(name = "First run - skippable introduction", showBackground = true, widthDp = 393, heightDp = 852)
@Preview(name = "First run - large text", showBackground = true, widthDp = 360, heightDp = 960, fontScale = 1.6f)
@Composable private fun FirstRunPreview() {
    PhysicalLinkTheme(dynamicColor = false) {
        DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, showIntroduction = true)
    }
}
