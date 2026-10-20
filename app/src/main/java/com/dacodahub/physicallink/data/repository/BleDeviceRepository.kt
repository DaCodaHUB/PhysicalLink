package com.dacodahub.physicallink.data.repository

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import androidx.core.content.ContextCompat
import com.dacodahub.physicallink.data.ble.BleConnectionManager
import com.dacodahub.physicallink.data.ble.BleScanner
import com.dacodahub.physicallink.domain.model.ConnectionState
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Application-scoped owner of the single BLE session; independent of screen lifetimes. */
@Singleton
class BleDeviceRepository @Inject constructor(
    @ApplicationContext context: Context
) : DeviceRepository {
    private val scanner = BleScanner(context)
    private val connectionManager = BleConnectionManager(context)

    override val connectedDeviceId = connectionManager.deviceId

    init {
        // Application-scoped receiver; broadcasts invalidate sessions, never reconnect them.
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refreshBluetoothState() }
        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    override val scanState = scanner.state
    override val devices = scanner.devices
    override val connectionState = connectionManager.connectionState
    override val capabilities = connectionManager.capabilities
    override val sensorValues = connectionManager.sensorValues

    override fun startScan() {
        if (connectionState.value == ConnectionState.Connected || connectionState.value == ConnectionState.Connecting ||
            connectionState.value == ConnectionState.Discovering) return
        connectionManager.clearConnectionError()
        scanner.startScan()
    }

    override fun stopScan() = scanner.stopScan()

    override fun connect(address: String) {
        scanner.stopScan()
        connectionManager.connect(address)
    }

    override fun refreshBluetoothState() {
        scanner.refreshEnvironment()
        connectionManager.refreshEnvironment()
    }

    override fun disconnect() = connectionManager.disconnect()
}
