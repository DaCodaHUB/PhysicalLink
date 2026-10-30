package com.dacodahub.physicallink.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import android.os.SystemClock
import com.dacodahub.physicallink.domain.model.LiveSensorState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.SensorManifest

@Composable
internal fun DeviceDiagnosticsScreen(metadata: SensorManifest, live: LiveSensorState, onDisconnect: () -> Unit, onBack: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = SystemClock.elapsedRealtime() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Device Diagnostics", style = MaterialTheme.typography.titleLarge)
            Text(metadata.device, style = MaterialTheme.typography.headlineSmall)
            Text("Protocol version 2\nPersistent device UUID: ${metadata.deviceId}\nEpoch: ${metadata.epoch}\nSensor count: ${metadata.sensors.size}")
            Text("Metadata retrieval complete")
            Text(when {
                !live.synchronized -> "Synchronizing initial state..."
                live.capturing -> "Refreshing state (request ${live.requestId})..."
                else -> "State synchronized (request ${live.requestId}); live notifications enabled"
            })
            TextButton(onClick = onBack) { Text("Back to devices") }
            Text("Stale means acquisition age is at least 30 seconds.")
        }
        items(metadata.sensors, key = { it.id }) { sensor ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(sensor.name, style = MaterialTheme.typography.titleMedium)
                    Text("ID: ${sensor.id}\nUnit: ${sensor.unit.ifEmpty { "(none)" }}")
                    val reading = live.readings[sensor.id]
                    if (reading == null) Text("Waiting for validated state") else {
                        Text("Value: ${reading.value?.toString() ?: "Unavailable"} ${sensor.unit}")
                        Text("Availability: ${reading.availability(now)}\nSequence: ${reading.sequence}\nAcquisition age: ${reading.acquisitionAgeMs(now)?.let { "$it ms" } ?: "unknown (unavailable)"}")
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onDisconnect) { Text("Disconnect") } }
    }
}
