
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

                when (connectionState) {
                    ConnectionState.ConnectionLost -> {
                        Text("Connection lost. Check that the ESP32 is powered on.")

                        Button(onClick = viewModel::startScan) {
                            Text("Scan again")
                        }
                    }

                    is ConnectionState.Error -> {
                        Text((connectionState as ConnectionState.Error).message)

                        Button(onClick = viewModel::startScan) {
                            Text("Try again")
                        }
                    }

                    else -> Unit
                }

                devices.forEach { device ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(device.name)
                            Text(device.address)
                            Text("RSSI: ${device.rssi} dBm")
                            Button(onClick = { viewModel.connect(device.address) }) {
                                Text("Connect")
                            }
                        }
                    }
                }

                Text("Connection: $connectionState")

                if (connectionState is ConnectionState.Connected) {
                    Button(onClick = viewModel::disconnect) {
                        Text("Disconnect")
                    }
                }

                if (connectionState is ConnectionState.Connected) {
                    Text("Soil: ${readings.soil}")
                    Text("Temperature: ${readings.temperature} °C")
                    Text("Humidity: ${readings.humidity} %")
                    Text("Pressure: ${readings.pressure} hPa")
                }
            }
        }
    }
}
