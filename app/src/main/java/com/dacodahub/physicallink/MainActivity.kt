package com.dacodahub.physicallink

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import com.dacodahub.physicallink.ui.SensorDashboard
import com.dacodahub.physicallink.ui.theme.PhysicalLinkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val devices by viewModel.devices.collectAsStateWithLifecycle()

            val connectionState by viewModel.connectionState
                .collectAsStateWithLifecycle()

            val capabilities by viewModel.capabilities
                .collectAsStateWithLifecycle()

            val sensorValues by viewModel.sensorValues
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

            PhysicalLinkTheme {
                SensorDashboard(
                    devices = devices,
                    connectionState = connectionState,
                    capabilities = capabilities,
                    sensorValues = sensorValues,
                    onScan = {
                        val granted = permissions.all {
                            checkSelfPermission(it) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                        if (granted) {
                            viewModel.startScan()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    onStopScan = viewModel::stopScan,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect
                )
            }
        }
    }
}
