package com.dacodahub.physicallink.domain.repository

import com.dacodahub.physicallink.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    /** Selected session address, retained after disconnect; connectionState indicates availability. */
    val connectedDeviceId: StateFlow<String?>
    val scanState: StateFlow<ScanState>
    val devices: StateFlow<List<DiscoveredDevice>>
    val connectionState: StateFlow<ConnectionState>
    val capabilities: StateFlow<DeviceCapabilities?>
    val sensorValues: StateFlow<Map<String, String>>
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    fun refreshBluetoothState()
    fun disconnect()
}
