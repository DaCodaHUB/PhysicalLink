package com.dacodahub.physicallink.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.R
import com.dacodahub.physicallink.core.designsystem.DeviceConnectionText
import com.dacodahub.physicallink.domain.model.ConnectionState

@Composable
internal fun DeviceEmblem(modifier: Modifier = Modifier) {
    Surface(modifier.size(52.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_device), contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun ConnectedDeviceCard(name: String, rssi: Int?, sensorCount: Int?,
    onDashboard: () -> Unit, onDiagnostic: (() -> Unit)?, onDisconnect: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large, elevation = CardDefaults.elevatedCardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                DeviceEmblem()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    DeviceConnectionText(ConnectionState.Connected)
                    val details = listOfNotNull(rssi?.let { "Last scan RSSI $it dBm" }, sensorCount?.let { "$it sensors" })
                    if (details.isNotEmpty()) Text(details.joinToString(" / "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onDashboard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Open dashboard")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onDiagnostic?.let { action -> FilledTonalButton(onClick = action) { Text("Device Diagnostics") } }
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}
