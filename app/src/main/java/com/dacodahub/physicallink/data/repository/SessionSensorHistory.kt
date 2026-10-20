package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.domain.model.*
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single-session, bounded ingestion. UI snapshots are explicitly published by the owner at 4Hz. */
class SessionSensorHistory(
    private val clock: MonotonicClock,
    private val maxSamples: Int = 4096,
    private val maxSensors: Int = 64,
    private val retentionMs: Long = 900_000
) {
    init { require(maxSamples > 0 && maxSensors > 0 && retentionMs > 0) }
    private class Buffer {
        val points = ArrayDeque<NumericSample>()
        var segment = 0L
        var lastSuccessful: Long? = null
        var numeric: Double? = null
        var valid = false
        var dirty = true
        var cached = SensorTimeline()
    }
    private val buffers = linkedMapOf<SensorSource, Buffer>()
    private var device: String? = null
    private var session = 0L
    private var active = false
    private var dirty = false
    private val _state = MutableStateFlow(SensorHistorySnapshot())
    val state = _state.asStateFlow()

    @Synchronized fun startSession(deviceId: String) {
        require(deviceId.isNotBlank())
        device = deviceId; session++; active = true; buffers.clear(); dirty = true
        publish()
    }
    @Synchronized fun endSession() {
        active = false; dirty = true
        publish()
    }
    @Synchronized fun record(capability: DeviceCapability, raw: String) {
        val id = device ?: return
        if (!active || capability.id.isBlank()) return
        val source = SensorSource(id, capability.id)
        val buffer = buffers[source] ?: if (buffers.size < maxSensors) Buffer().also { buffers[source] = it } else return
        val now = clock.nowMs()
        val numberType = capability.type.equals("number", true)
        val numeric = raw.takeIf { numberType && it.length <= 256 }?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val valid = if (numberType) numeric != null else raw.isNotBlank()
        if (buffer.lastSuccessful?.let { now < it } == true) return
        if (valid) {
            buffer.lastSuccessful = now
            if (numberType && numeric != null) {
                if (buffer.points.lastOrNull()?.timeMs?.let { now < it } == true) return
                buffer.points.addLast(NumericSample(now, numeric, buffer.segment))
                while (buffer.points.size > maxSamples) buffer.points.removeFirst()
            }
        } else if (buffer.valid) buffer.segment++ // Invalid/missing updates break chart continuity.
        buffer.numeric = if (numberType && valid) numeric else null
        buffer.valid = valid
        prune(buffer, now)
        buffer.dirty = true; dirty = true
    }
    private fun prune(buffer: Buffer, now: Long) {
        while (buffer.points.firstOrNull()?.timeMs?.let { it < now - retentionMs } == true) {
            buffer.points.removeFirst(); buffer.dirty = true; dirty = true
        }
    }
    @Synchronized fun publish(): SensorHistorySnapshot {
        val now = clock.nowMs()
        buffers.values.forEach { prune(it, now) }
        if (!dirty) return _state.value
        val timelines = buffers.mapValues { (_, buffer) ->
            if (buffer.dirty) {
                buffer.cached = SensorTimeline(buffer.points.toList(), buffer.lastSuccessful, buffer.numeric, buffer.valid)
                buffer.dirty = false
            }
            buffer.cached
        }
        _state.value = SensorHistorySnapshot(device, session, active, timelines)
        dirty = false
        return _state.value
    }
}
