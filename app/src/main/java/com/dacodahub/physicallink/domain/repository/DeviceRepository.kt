package com.dacodahub.physicallink.domain.repository

import com.dacodahub.physicallink.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface DeviceRepository {
    val devices: StateFlow<List<DiscoveredDevice>>
    val connectionState: StateFlow<ConnectionState>
    val capabilities: StateFlow<DeviceCapabilities?>
    val sensorValues: StateFlow<Map<String, String>>
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    fun disconnect()
}
