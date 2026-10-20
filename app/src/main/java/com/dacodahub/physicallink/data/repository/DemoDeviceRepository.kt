package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.domain.repository.DeviceRepository
import java.util.Locale
import kotlin.math.sin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Virtual dashboard source. Owns no Bluetooth objects and never delegates BLE operations. */
class DemoDeviceRepository(private val clock: MonotonicClock) : DeviceRepository {
    companion object {
        // Not a BLE address; stable across sessions/processes, isolated from hardware layouts.
        const val DEVICE_ID = "physicallink:demo:sensor-station:v1"
        val CAPABILITIES = DeviceCapabilities(1, "Demo Sensor Station", listOf(
            DeviceCapability("temperature", "0000d001-0000-1000-8000-00805f9b34fb", "\u00b0C", "number"),
            DeviceCapability("humidity", "0000d002-0000-1000-8000-00805f9b34fb", "%", "number"),
            DeviceCapability("pressure", "0000d003-0000-1000-8000-00805f9b34fb", "hPa", "number"),
            DeviceCapability("soil_moisture", "0000d004-0000-1000-8000-00805f9b34fb", "%", "number")
        ))
    }
    private val history = SessionSensorHistory(clock)
    override val sensorHistory = history.state
    override val connectedDeviceId = MutableStateFlow<String?>(DEVICE_ID).asStateFlow()
    override val scanState = MutableStateFlow<ScanState>(ScanState.Idle).asStateFlow()
    override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList()).asStateFlow()
    // Virtual availability is separate from history.active (whether simulation is currently running).
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected).asStateFlow()
    override val capabilities = MutableStateFlow<DeviceCapabilities?>(CAPABILITIES).asStateFlow()
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())
    override val sensorValues = values.asStateFlow()
    private var running = false
    private var step = 0L
    override fun monotonicNowMs() = clock.nowMs()

    /** Caller owns cancellation, normally repeatOnLifecycle(RESUMED) on the dashboard route. */
    suspend fun simulateWhileVisible() {
        check(!running) { "Demo simulation already running" }
        running = true
        try {
            history.startSession(DEVICE_ID)
            while (currentCoroutineContext().isActive) {
                val t = step++.toDouble()
                val readings = listOf(
                    24.0 + 4.5 * sin(t / 37.0) + 1.0 * sin(t / 13.0),
                    52.5 + 18.0 * sin(t / 49.0 + 1.0) + 3.0 * sin(t / 17.0),
                    1010.0 + 16.0 * sin(t / 83.0 + 2.0) + 3.0 * sin(t / 29.0),
                    52.5 + 27.0 * sin(t / 67.0 + 3.0) + 4.0 * sin(t / 23.0)
                )
                val next = CAPABILITIES.capabilities.zip(readings).associate { (sensor, reading) ->
                    val raw = String.format(Locale.ROOT, "%.2f", reading)
                    history.record(sensor, raw)
                    sensor.id to raw
                }
                history.publish()
                values.value = next
                delay(1000)
            }
        } finally {
            history.endSession()
            running = false
        }
    }
    // Deliberately inert: there is no radio or device to scan, connect, or write to.
    override fun startScan() = Unit
    override fun stopScan() = Unit
    override fun connect(address: String) = Unit
    override fun refreshBluetoothState() = Unit
    override fun disconnect() = Unit
}
