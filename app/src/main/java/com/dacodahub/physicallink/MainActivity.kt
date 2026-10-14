
package com.dacodahub.physicallink

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacodahub.physicallink.ble.ConnectionState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val devices by viewModel.devices.collectAsStateWithLifecycle()

            val connectionState by viewModel.connectionState
                .collectAsStateWithLifecycle()

            val readings by viewModel.readings
                .collectAsStateWithLifecycle()

            val permissions = if (Build.VERSION.SDK_INT >= 31) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    if (results.values.all { it }) {
                        viewModel.startScan()
                    }
                }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "PhysicalLink",
                    style = MaterialTheme.typography.headlineMedium
                )

                Button(onClick = {
                    val granted = permissions.all {
                        checkSelfPermission(it) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                    }

                    if (granted) {
                        viewModel.startScan()
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                }) {
                    Text("Scan for devices")
                }

                OutlinedButton(onClick = viewModel::stopScan) {
                    Text("Stop scan")
                }

                val showDevices = when (connectionState) {
                    ConnectionState.Disconnected,
                    ConnectionState.ConnectionLost,
                    is ConnectionState.Error -> true

                    else -> false
                }

                if (showDevices) {
                    Text(
                        "Nearby devices",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (devices.isEmpty()) {
                        Text("No devices found. Tap Scan to search.")
                    }

                    devices.forEach { device ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    device.name,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text("RSSI: ${device.rssi} dBm")

                                Button(
                                    onClick = {
                                        viewModel.connect(device.address)
                                    }
                                ) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }

                when (connectionState) {
                    ConnectionState.ConnectionLost -> {
                        Text("Connection lost. Select a device to reconnect.")
                    }

                    ConnectionState.Connecting -> {
                        Text("Connecting...")
                    }

                    ConnectionState.Discovering -> {
                        Text("Discovering services...")
                    }

                    ConnectionState.Connected -> {
                        Text("Soil: ${readings.soil}")
                        Text("Temperature: ${readings.temperature} °C")
                        Text("Humidity: ${readings.humidity} %")
                        Text("Pressure: ${readings.pressure} hPa")
                    }

                    is ConnectionState.Error -> {
                        Text((connectionState as ConnectionState.Error).message)
                    }

                    ConnectionState.Disconnected -> Unit
                }
            }
        }
    }
}
