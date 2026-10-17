package com.dacodahub.physicallink.feature

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import com.dacodahub.physicallink.feature.dashboard.DashboardViewModel
import com.dacodahub.physicallink.feature.devices.DevicesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class SharedDeviceSessionTest {
    @Test
    fun `both view models expose the same session and forward explicit actions`() {
        val repository = FakeDeviceRepository()
        val devices = DevicesViewModel(repository)
        val dashboard = DashboardViewModel(repository)
        assertSame(devices.connectionState, dashboard.connectionState)
        assertSame(repository.devices, devices.devices)
        assertSame(repository.capabilities, dashboard.capabilities)
        assertSame(repository.sensorValues, dashboard.sensorValues)

        devices.startScan()
        assertTrue(repository.scanning)
        devices.stopScan()
        assertFalse(repository.scanning)
        devices.connect("device-address")
        assertEquals("device-address", repository.connectedAddress)
        assertEquals(ConnectionState.Connected, dashboard.connectionState.value)

        repository.capabilities.value = DeviceCapabilities(1, "Custom device", emptyList())
        repository.sensorValues.value = mapOf("custom_sensor" to "12.5")
        assertEquals("Custom device", dashboard.capabilities.value?.device)
        assertEquals("12.5", dashboard.sensorValues.value["custom_sensor"])
        dashboard.disconnect()
        assertEquals(ConnectionState.Disconnected, devices.connectionState.value)
    }

    private class FakeDeviceRepository : DeviceRepository {
        override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
        override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        override val capabilities = MutableStateFlow<DeviceCapabilities?>(null)
        override val sensorValues = MutableStateFlow<Map<String, String>>(emptyMap())
        var scanning = false
        var connectedAddress: String? = null
        override fun startScan() { scanning = true }
        override fun stopScan() { scanning = false }
        override fun connect(address: String) {
            connectedAddress = address
            connectionState.value = ConnectionState.Connected
        }
        override fun disconnect() { connectionState.value = ConnectionState.Disconnected }
    }
}
