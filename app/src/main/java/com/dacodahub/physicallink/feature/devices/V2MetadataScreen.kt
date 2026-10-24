package com.dacodahub.physicallink.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.V2Metadata

@Composable
internal fun V2MetadataScreen(metadata: V2Metadata, onDisconnect: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Experimental Device Details / Metadata", style = MaterialTheme.typography.titleLarge)
            Text(metadata.device, style = MaterialTheme.typography.headlineSmall)
            Text("Protocol version 2\nPersistent device UUID: ${metadata.deviceId}\nEpoch: ${metadata.epoch}\nSensor count: ${metadata.sensors.size}")
            Text("Metadata retrieval complete")
            Text("Live sensor monitoring is not available until the v2 State/Live transport is implemented.")
        }
        items(metadata.sensors, key = { it.id }) { sensor ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(sensor.name, style = MaterialTheme.typography.titleMedium)
                    Text("ID: ${sensor.id}\nUnit: ${sensor.unit.ifEmpty { "(none)" }}")
                }
            }
        }
        item { OutlinedButton(onClick = onDisconnect) { Text("Disconnect") } }
    }
}
