package com.dacodahub.physicallink.data.repository

import android.content.Context
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

    override val devices = scanner.devices
    override val connectionState = connectionManager.connectionState
    override val capabilities = connectionManager.capabilities
    override val sensorValues = connectionManager.sensorValues

    override fun startScan() {
        if (connectionState.value == ConnectionState.Connected) return
        connectionManager.clearConnectionError()
        scanner.startScan()
    }

    override fun stopScan() = scanner.stopScan()

    override fun connect(address: String) {
        scanner.stopScan()
        connectionManager.connect(address)
    }

    override fun disconnect() = connectionManager.disconnect()
}
