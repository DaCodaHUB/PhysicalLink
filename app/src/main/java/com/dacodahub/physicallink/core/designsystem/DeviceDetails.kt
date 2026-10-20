package com.dacodahub.physicallink.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.*

/** Diagnostics are read-only and separate from the connect/read/edit flow. */
@Composable
internal fun DeviceDetails(
    name: String?, address: String?, rssi: Int?, capabilities: DeviceCapabilities?,
    connectionState: ConnectionState, scanState: ScanState? = null,
    onDismiss: () -> Unit, onDisconnect: (() -> Unit)? = null
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Device details") },
        text = {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailField("Project", name?.takeIf { it.isNotBlank() } ?: "No project selected")
                    DetailField("Bluetooth address", address ?: "Not available")
                    if (rssi != null) DetailField("Signal at last scan", "$rssi dBm (not a live measurement)")
                    DetailField("Connection", when (connectionState) {
                        is ConnectionState.Error -> connectionState.message
                        else -> connectionLabel(connectionState)
                    })
                    if (scanState is ScanState.Error) DetailField("Scan diagnostics", scanState.message)
                    DetailField("Reading timestamps", "Not available in the current reading stream. Freshness cannot be determined.")
                    if (capabilities != null) {
                        DetailField("Capability protocol", "Version ${capabilities.version}")
                        Text("Sensors (${capabilities.capabilities.size})", style = MaterialTheme.typography.titleSmall)
                        capabilities.capabilities.forEach { sensor ->
                            DetailField(sensor.id, "UUID: ${sensor.uuid}\nType: ${sensor.type}\nUnit: ${sensor.unit.ifEmpty { "Not supplied" }}")
                        }
                    } else Text("Sensor details appear after discovery.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (onDisconnect != null) TextButton(onClick = onDisconnect) {
                Text(if (connectionState == ConnectionState.Connected) "Disconnect" else "Cancel connection")
            }
        })
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
