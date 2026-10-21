package com.dacodahub.physicallink.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val gettingStartedSections = listOf(
    "Bring a compatible project" to "Use a BLE-capable ESP32 running firmware that implements the PhysicalLink sensor service and capability format. The included soil-monitor example uses a BME280 and an analog soil sensor. Ordinary BLE gadgets are not automatically supported.",
    "Prepare the firmware" to "Example source and its PlatformIO build configuration are in this project's firmware/soil-monitor folder. Build and flash matching firmware using your development tools; the Android app does not flash boards. Check wiring, power, and sensor initialization before scanning.",
    "Make your project discoverable" to "The current Android scanner looks for the exact advertised name PhysicalLink Soil. Keep the board nearby, powered, and advertising. A device with a different name will not appear, even if it has Bluetooth. Advertising a matching name alone does not guarantee compatible firmware.",
    "Discover sensors" to "After you connect, PhysicalLink reads a versioned JSON description of sensor IDs, characteristic UUIDs, data types, and units. It then reads or subscribes to the described sensor characteristics as supported. Device details shows what was actually discovered; arbitrary BLE profiles are not supported.",
    "Connect your board" to "From Devices, tap Scan, then select your project. Permissions are requested only when needed for scanning. Keep the board nearby while connecting. If another phone is connected to the board, disconnect it first. Nothing connects automatically.",
    "Make the dashboard yours" to "Use Edit dashboard to drag widgets or open a widget's settings. Move up/down is an alternative to dragging. Choose Value, Chart, Gauge, or Range for numeric sensors. Set explicit bounds for Gauge and Range. Resize, duplicate, hide, or restore widgets using Add widget. Done keeps your layout; Reset layout asks before clearing customization.",
    "Read the status" to "Live means a recent successful sensor update; a connected board can still have stale readings. Charts contain only current-session samples and restart after reconnecting. Layouts are saved locally per device. Demo Mode uses clearly labeled simulated readings and a separate saved layout.",
    "If a scan is empty or fails" to "Check that Bluetooth is on and the board advertises as PhysicalLink Soil. Stop other Bluetooth scans, wait a moment, and retry. On Android 11 or earlier, Location permission and the system Location setting may be needed for BLE scans. Open Device details for the scan diagnostic.",
    "If connecting fails" to "Check board power and range, then retry. If the service, a sensor characteristic, or capability JSON is missing or invalid, flash matching PhysicalLink firmware and restart the board. Device details preserves the available error message; failed discovery cannot provide a sensor list. Try Demo to explore without compatible hardware."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GettingStarted(onDismiss: () -> Unit, onReplayIntroduction: () -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp)) {
            Text("Getting Started", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                gettingStartedSections.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            TextButton(onClick = onReplayIntroduction, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Replay Introduction") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Close guide") }
        }
    }
}

@Composable
internal fun PermissionExplanation(legacy: Boolean, onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Find your sensor project") },
        text = { Text(if (legacy) "Android needs Location permission to scan for nearby BLE projects. You may also need to enable the system Location setting. Demo Mode works without this permission."
            else "Nearby devices permission lets PhysicalLink scan for and connect to compatible BLE sensor projects. Demo Mode works without this permission.") },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } })
}
