package com.dacodahub.physicallink.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.feature.devices.DevicesContent

// Metadata matches firmware/soil-monitor/src/main.cpp. All sensor readings stay null.
private val capabilities = DeviceCapabilities(1, "PhysicalLink Soil", listOf(
    DeviceCapability("soil", "5678", "raw", "number"),
    DeviceCapability("temperature", "5679", "C", "number"),
    DeviceCapability("humidity", "567a", "%", "number"),
    DeviceCapability("pressure", "567b", "hPa", "number")
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
@Preview(name = "Future visualizations - explicitly unavailable", showBackground = true, widthDp = 393)
@Composable private fun UnavailableRangePreview() {
    PhysicalLinkTheme(dynamicColor = false) { UnavailableVisualization("Sensor", WidgetPresentation.RANGE) }
}

// Advertisement fixtures below are preview-only, not observations from real hardware.
@Preview(name = "Devices - compact rows", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun DevicesFoundPreview() {
    PhysicalLinkTheme(dynamicColor = false) {
        DevicesContent(listOf(DiscoveredDevice("PhysicalLink Soil", "preview-only", -58)),
            ConnectionState.Disconnected, ScanState.Idle)
    }
}

@Preview(name = "Widget configuration - Value only", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun WidgetConfigurationPreview() {
    PhysicalLinkTheme(dynamicColor = false) {
        val widget = dashboardState(deviceId, capabilities, emptyMap(), null).widgets.first()
        WidgetConfigurationSheet(widget, capabilities.device, 0, 4, DashboardActions(), onDismiss = {})
    }
}
