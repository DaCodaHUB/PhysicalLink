package com.dacodahub.physicallink.data.ble

import com.dacodahub.physicallink.domain.model.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LiveCodec {
    fun record(bytes: ByteArray, count: Int, now: Long): SensorReading {
        require(bytes.size == 20 && count in 1..16) { "Live record must be exactly 20 bytes" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(b.get().toInt() and 255 == 0x20) { "Invalid live tag" }
        val channel = b.get().toInt() and 255
        val status = b.get().toInt() and 255
        require(channel < count && status in 0..1 && b.get() == 0.toByte()) { "Invalid live channel/status/reserved" }
        val sequence = b.int.toLong() and 0xffffffffL
        val age = b.int.toLong() and 0xffffffffL
        val bits = b.long
        val number = Double.fromBits(bits)
        require(number.isFinite()) { "Nonfinite live number" }
        if (status == 0) require(sequence > 0 && age != 0xffffffffL) { "Invalid valid reading sequence/age" }
        else require(age == 0xffffffffL && bits == 0L) { "Invalid unavailable encoding" }
        return SensorReading(channel, if (status == 0) number else null, sequence, age, now)
    }
    data class Image(val requestId: Int, val records: List<SensorReading>)
    fun image(bytes: ByteArray, metadata: SensorManifest, now: Long): Image {
        require(bytes.size == 16 + metadata.sensors.size * 20) { "Invalid state image length" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(b.get() == 0x21.toByte() && (b.get().toInt() and 255) == metadata.sensors.size) { "Invalid state image header/count" }
        val request = b.short.toInt() and 65535
        val epoch = java.lang.Long.toUnsignedString(b.long, 16).padStart(16, '0')
        require(epoch == metadata.epoch && b.int == 0) { "Invalid state image epoch/reserved" }
        val records = metadata.sensors.indices.map { i ->
            record(bytes.copyOfRange(16 + i * 20, 36 + i * 20), metadata.sensors.size, now).also {
                require(it.channel == i) { "State channels must be ordered" }
                require(request != 0 || (it.value == null && it.sequence == 0L)) { "Invalid initial image" }
            }
        }
        return Image(request, records)
    }
}

/** Session-local sequence domain, bounded to one latest record per channel while capturing. */
class LiveSession(private val metadata: SensorManifest) {
    var state = LiveSensorState()
        private set
    private val buffered = mutableMapOf<Int, SensorReading>()
    private var previousRequest = 0
    var gap = false
        private set
    fun beginCapture(): ByteArray {
        check(!state.capturing && state.requestId < 65535) { "Capture exhausted or already outstanding; reconnect" }
        previousRequest = state.requestId
        state = state.copy(capturing = true, requestId = previousRequest + 1)
        gap = false
        return byteArrayOf(2, state.requestId.toByte(), (state.requestId shr 8).toByte())
    }
    fun notification(bytes: ByteArray, now: Long) {
        val record = LiveCodec.record(bytes, metadata.sensors.size, now)
        val id = metadata.sensors[record.channel].id
        val old = buffered[record.channel] ?: state.readings[id]
        if (old != null && record.sequence <= old.sequence) return
        if (old != null && record.sequence > old.sequence + 1) gap = true
        if (state.capturing || !state.synchronized) buffered[record.channel] = record
        else state = state.copy(readings = state.readings + (id to record))
    }
    fun acceptImage(bytes: ByteArray, now: Long): Boolean {
        check(state.capturing)
        val image = LiveCodec.image(bytes, metadata, now)
        if (image.requestId != state.requestId) {
            require(image.requestId == previousRequest) { "Unexpected capture request ID" }
            return false
        }
        val merged = metadata.sensors.indices.associate { i ->
            val id = metadata.sensors[i].id
            var reading = image.records[i]
            val old = state.readings[id]
            if (old != null && old.sequence > reading.sequence) reading = old
            val live = buffered[i]
            if (live != null && live.sequence > reading.sequence) reading = live
            id to reading
        }
        buffered.clear()
        state = state.copy(readings = merged, synchronized = true, capturing = false)
        return true
    }
}
