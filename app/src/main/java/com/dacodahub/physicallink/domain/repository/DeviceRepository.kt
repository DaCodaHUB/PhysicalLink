package com.dacodahub.physicallink.domain.repository

import com.dacodahub.physicallink.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    /** Selected session address, retained after disconnect; connectionState indicates availability. */
    val connectedDeviceId: StateFlow<String?>
    val scanState: StateFlow<ScanState>
    val devices: StateFlow<List<DiscoveredDevice>>
    val connectionState: StateFlow<ConnectionState>
    val v2Metadata: StateFlow<V2Metadata?> get() = kotlinx.coroutines.flow.MutableStateFlow(null)
    val capabilities: StateFlow<DeviceCapabilities?>
    val sensorHistory: StateFlow<SensorHistorySnapshot>
    val sensorValues: StateFlow<Map<String, String>>
    /** Same monotonic time base used for this repository's receive timestamps. */
    fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    fun refreshBluetoothState()
    fun disconnect()
}
