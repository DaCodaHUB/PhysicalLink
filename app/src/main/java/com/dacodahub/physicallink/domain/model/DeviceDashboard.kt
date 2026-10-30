package com.dacodahub.physicallink.domain.model

/** Presentation mapping after protocol validation. Channel position is never a layout identity. */
// Persisted namespace: keep v2: even though source names no longer have a version prefix.
fun SensorManifest.dashboardDeviceId(): String = "v2:$deviceId"
fun SensorManifest.dashboardCapabilities() = DeviceCapabilities(2, device, sensors.map {
    DeviceCapability(it.id, "v2:${it.id}", it.unit, "number", it.name)
})

/** One ingestion per sensor sequence, including periodic state images. Owned by one connection. */
class DashboardUpdates {
    private val sequences = mutableMapOf<String, Long>()
    fun accept(state: LiveSensorState, consume: (String, SensorReading) -> Unit) {
        if (!state.synchronized) return
        state.readings.forEach { (id, reading) ->
            if (sequences[id]?.let { reading.sequence <= it } != true) {
                sequences[id] = reading.sequence
                consume(id, reading)
            }
        }
    }
}
