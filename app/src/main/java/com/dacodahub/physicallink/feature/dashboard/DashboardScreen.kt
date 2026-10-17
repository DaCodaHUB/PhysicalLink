package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacodahub.physicallink.core.designsystem.ConnectionStatus
import com.dacodahub.physicallink.domain.model.ConnectionState

@Composable
fun DashboardScreen(onDevices: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val sensorValues by viewModel.sensorValues.collectAsStateWithLifecycle()

    // Navigation observes the session; leaving this screen never tears it down.
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.Disconnected ||
            connectionState == ConnectionState.ConnectionLost || connectionState is ConnectionState.Error) {
            onDevices()
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (maxWidth >= 600.dp) 2 else 1),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Dashboard", style = MaterialTheme.typography.headlineLarge)
                        ConnectionStatus(connectionState)
                        TextButton(onClick = onDevices) { Text("Devices") }
                        if (connectionState == ConnectionState.Connected) {
                            Text("Connected device", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(capabilities?.device?.takeIf { it.isNotBlank() } ?: "PhysicalLink device",
                                style = MaterialTheme.typography.headlineSmall)
                            OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                            Text("Sensors", style = MaterialTheme.typography.titleLarge)
                            if (capabilities == null) {
                                Text("Discovering sensor capabilities?")
                            } else if (capabilities?.capabilities?.isEmpty() == true) {
                                Text("This device has no available sensors.")
                            }
                        }
                    }
                }
                if (connectionState == ConnectionState.Connected) {
                    items(capabilities?.capabilities.orEmpty()) { capability ->
                        SensorCard(capability, sensorValues[capability.id])
                    }
                }
            }
        }
    }
}
