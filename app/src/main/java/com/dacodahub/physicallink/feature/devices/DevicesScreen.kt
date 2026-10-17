package com.dacodahub.physicallink.feature.devices

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacodahub.physicallink.core.designsystem.ConnectionStatus
import com.dacodahub.physicallink.domain.model.ConnectionState

@Composable
fun DevicesScreen(onOpenDashboard: () -> Unit, viewModel: DevicesViewModel = hiltViewModel()) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.startScan()
    }
    val showDevices = connectionState == ConnectionState.Disconnected ||
        connectionState == ConnectionState.ConnectionLost || connectionState is ConnectionState.Error

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("PhysicalLink", style = MaterialTheme.typography.headlineLarge)
                    Text("Devices", style = MaterialTheme.typography.titleLarge)
                    ConnectionStatus(connectionState)
                    ScanControls(
                        onScan = {
                            if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                                viewModel.startScan()
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        },
                        onStopScan = viewModel::stopScan
                    )
                    if (!showDevices) {
                        Button(onClick = onOpenDashboard) { Text("Open dashboard") }
                        if (connectionState == ConnectionState.Connected) {
                            OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                        }
                    }
                }
            }
            if (showDevices) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nearby devices", style = MaterialTheme.typography.titleLarge)
                        if (devices.isEmpty()) Text("No devices found. Tap Scan to search.")
                    }
                }
                items(devices, key = { it.address }) { device ->
                    DeviceCard(device) {
                        viewModel.connect(device.address)
                        onOpenDashboard()
                    }
                }
            }
        }
    }
}
