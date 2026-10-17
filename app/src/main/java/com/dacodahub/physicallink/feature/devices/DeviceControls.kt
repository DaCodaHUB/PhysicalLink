package com.dacodahub.physicallink.feature.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.DiscoveredDevice

@Composable
internal fun ScanControls(onScan: () -> Unit, onStopScan: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onScan) { Text("Scan for devices") }
        OutlinedButton(onClick = onStopScan) { Text("Stop scan") }
    }
}

@Composable
internal fun DeviceCard(device: DiscoveredDevice, onConnect: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("RSSI: ${device.rssi} dBm", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}
