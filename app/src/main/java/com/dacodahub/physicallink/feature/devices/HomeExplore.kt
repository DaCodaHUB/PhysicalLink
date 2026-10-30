package com.dacodahub.physicallink.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import com.dacodahub.physicallink.R



/** Home-page actions reuse existing callbacks; they own no connection or navigation state. */
@Composable
internal fun ExploreDiscovery(connected: Boolean, connecting: Boolean, scanning: Boolean,
    onFind: () -> Unit, onStop: () -> Unit) {
    HomeExploreCard("Find Devices", when {
        connected -> "Connect another project after disconnecting this one."
        connecting -> "Finish or cancel the current connection first."
        scanning -> "Looking for nearby PhysicalLink devices."
        else -> "Discover nearby ESP32 projects over Bluetooth."
    }, demo = false, scanning = scanning) {
        if (connected || connecting) FilledTonalButton(onClick = onFind, enabled = !connecting,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("Find Devices") }
        else ScanControls(onFind, onStop, scanning)
    }
}

@Composable
internal fun ExploreDemo(onDemo: () -> Unit) {
    HomeExploreCard("Demo mode", "Explore a dashboard with simulated readings. No hardware needed.", demo = true) {
        TextButton(onClick = onDemo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try Demo") }
    }
}

@Composable
private fun HomeExploreCard(title: String, description: String, demo: Boolean, scanning: Boolean = false,
    action: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = MaterialTheme.shapes.medium,
                    color = if (demo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (demo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (demo) Icon(painterResource(R.drawable.ic_demo_dashboard), contentDescription = null, modifier = Modifier.size(24.dp))
                        else Icon(painterResource(R.drawable.ic_device), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, modifier = Modifier.semantics { if (scanning) liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Scanning stops after 15 seconds.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.align(Alignment.End)) { action() }
        }
    }
}
