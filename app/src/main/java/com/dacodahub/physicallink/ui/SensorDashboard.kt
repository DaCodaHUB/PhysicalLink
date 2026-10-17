package com.dacodahub.physicallink.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.ble.ConnectionState
import com.dacodahub.physicallink.ble.DeviceCapabilities
import com.dacodahub.physicallink.ble.DeviceCapability
import com.dacodahub.physicallink.ble.DiscoveredDevice

@Composable
fun SensorDashboard(
    devices: List<DiscoveredDevice>,
    connectionState: ConnectionState,
    capabilities: DeviceCapabilities?,
    sensorValues: Map<String, String>,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val columns = if (maxWidth >= 600.dp) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("PhysicalLink", style = MaterialTheme.typography.headlineLarge)
                        ConnectionStatus(connectionState)
                        ScanControls(onScan, onStopScan)
                    }
                }
                if (connectionState == ConnectionState.Connected) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Connected device", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(capabilities?.device?.takeIf { it.isNotBlank() } ?: "PhysicalLink device",
                                style = MaterialTheme.typography.headlineSmall)
                            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                            Text("Sensors", style = MaterialTheme.typography.titleLarge)
                            if (capabilities == null) {
                                Text("Discovering sensor capabilities…")
                            } else if (capabilities.capabilities.isEmpty()) {
                                Text("This device has no available sensors.")
                            }
                        }
                    }
                    items(capabilities?.capabilities.orEmpty()) { capability ->
                        SensorCard(capability, sensorValues[capability.id])
                    }
                }
                val showDevices = connectionState == ConnectionState.Disconnected ||
                    connectionState == ConnectionState.ConnectionLost || connectionState is ConnectionState.Error
                if (showDevices) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nearby devices", style = MaterialTheme.typography.titleLarge)
                            if (devices.isEmpty()) Text("No devices found. Tap Scan to search.")
                        }
                    }
                    items(devices, key = { it.address }) { device ->
                        DeviceCard(device) { onConnect(device.address) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    val message = when (state) {
        ConnectionState.Connected -> "Connected"
        ConnectionState.Connecting -> "Connecting…"
        ConnectionState.Discovering -> "Discovering services…"
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.ConnectionLost -> "Connection lost. Select a device to reconnect."
        is ConnectionState.Error -> state.message
    }
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (state) {
        ConnectionState.Connected -> colors.primaryContainer to colors.onPrimaryContainer
        ConnectionState.ConnectionLost, is ConnectionState.Error -> colors.errorContainer to colors.onErrorContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ScanControls(onScan: () -> Unit, onStopScan: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onScan) { Text("Scan for devices") }
        OutlinedButton(onClick = onStopScan) { Text("Stop scan") }
    }
}

@Composable
private fun DeviceCard(device: DiscoveredDevice, onConnect: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("RSSI: ${device.rssi} dBm", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun SensorCard(capability: DeviceCapability, value: String?) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(sensorName(capability.id), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatSensorValue(value, capability.type), style = MaterialTheme.typography.headlineLarge)
            val unit = sensorUnit(capability.unit)
            if (unit.isNotEmpty()) {
                Text(unit, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
