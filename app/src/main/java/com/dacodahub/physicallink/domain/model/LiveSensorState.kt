package com.dacodahub.physicallink.domain.model

/** Receipt time is monotonic; age continues to advance without notifications. */
data class SensorReading(val channel: Int, val value: Double?, val sequence: Long, val ageMs: Long, val receivedAtMs: Long) {
    fun acquisitionAgeMs(nowMs: Long): Long? = value?.let { ageMs + (nowMs - receivedAtMs).coerceAtLeast(0) }
    fun availability(nowMs: Long): String = when {
        value == null -> "Unavailable"
        acquisitionAgeMs(nowMs)!! >= 30_000 -> "Stale"
        else -> "Valid"
    }
}
data class LiveSensorState(val readings: Map<String, SensorReading> = emptyMap(), val synchronized: Boolean = false, val capturing: Boolean = false, val requestId: Int = 0)
