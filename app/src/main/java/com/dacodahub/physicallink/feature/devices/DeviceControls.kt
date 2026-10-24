package com.dacodahub.physicallink.feature.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.ActionGlyph
import com.dacodahub.physicallink.core.designsystem.Glyph
import com.dacodahub.physicallink.domain.model.DiscoveredDevice

@Composable
internal fun ScanControls(onScan: () -> Unit, onStopScan: () -> Unit, scanning: Boolean) {
    FilledTonalButton(onClick = if (scanning) onStopScan else onScan,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = if (scanning) "Stop scanning" else "Scan for devices" }) {
        Text(if (scanning) "Stop" else "Scan")
    }
}

@Composable
internal fun DeviceCard(device: DiscoveredDevice, onConnect: () -> Unit, onDetails: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("device:${device.address}")
            .clickable(role = Role.Button, onClickLabel = "Connect to ${device.name}", onClick = onConnect)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Protocol v${device.protocolVersion}" + if (device.protocolVersion == 2) " ? Experimental" else "")
                Text(signalStrengthLabel(device.rssi), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDetails, modifier = Modifier.semantics { contentDescription = "Details for ${device.name}" }) {
                ActionGlyph(Glyph.INFO)
            }
        }
    }
}

/** Relative indication from the last advertisement, never a distance or live RSSI claim. */
internal fun signalStrengthLabel(rssi: Int): String = when (rssi) {
    in -60..-1 -> "Strong signal"
    in -75..-61 -> "Good signal"
    in -127..-76 -> "Weak signal"
    else -> "Signal unavailable"
}
