package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SessionSensorHistoryTest {
    private var now = 0L
    private val sensor = DeviceCapability("temperature", "5678", "C", "number")
    private val source = SensorSource("A", sensor.id)
    private fun history(max: Int = 4096, sensors: Int = 64, retention: Long = 900_000) =
        SessionSensorHistory(MonotonicClock { now }, max, sensors, retention)

    @Test fun `identical readings are recorded once per sensor not per widget and publish is batched`() {
        val history = history(); history.startSession("A")
        val initial = history.state.value
        repeat(100) { now++; history.record(sensor, "12") }
        assertSame(initial, history.state.value)
        val published = history.publish()
        assertEquals(1, published.sensors.size)
        assertEquals(100, published.sensors[source]!!.samples.size)
        assertSame(published, history.publish())
    }
    @Test fun `rapid updates have a hard count bound and a time bound`() {
        val history = history(32, retention = 1000); history.startSession("A")
        repeat(100_000) { now++; history.record(sensor, it.toString()) }
        assertEquals(32, history.publish().sensors[source]!!.samples.size)
        assertEquals(99999.0, history.state.value.sensors[source]!!.latestNumeric!!, 0.0)
        now += 1001
        val expired = history.publish().sensors[source]!!
        assertTrue(expired.samples.isEmpty())
        assertEquals(100_000L, expired.lastSuccessfulMs)
    }
    @Test fun `sensor count is bounded and sources are device scoped`() {
        val history = history(sensors = 2); history.startSession("A")
        repeat(10) { history.record(sensor.copy(id = "sensor$it"), "1") }
        assertEquals(2, history.publish().sensors.size)
        history.startSession("B"); history.record(sensor, "2")
        assertEquals(setOf(SensorSource("B", sensor.id)), history.publish().sensors.keys)
    }
    @Test fun `malformed nonfinite and missing values never enter numeric history or refresh success time`() {
        val history = history(); history.startSession("A"); history.record(sensor, "1")
        listOf("", " ", "oops", "NaN", "Infinity", "-Infinity", "1e9999").forEach {
            now++; history.record(sensor, it)
            val timeline = history.publish().sensors[source]!!
            assertEquals(1, timeline.samples.size)
            assertEquals(0L, timeline.lastSuccessfulMs)
            assertFalse(timeline.latestValid)
            assertNull(timeline.latestNumeric)
        }
        now++; history.record(sensor, "2")
        val samples = history.publish().sensors[source]!!.samples
        assertNotEquals(samples.first().segment, samples.last().segment)
    }
    @Test fun `text sensors have freshness without being interpreted as numeric samples`() {
        val history = history(); history.startSession("A")
        history.record(sensor.copy(type = "string"), "42")
        val timeline = history.publish().sensors[source]!!
        assertTrue(timeline.latestValid); assertTrue(timeline.samples.isEmpty()); assertNull(timeline.latestNumeric)
        assertEquals(ReadingFreshness.LIVE, readingFreshness(timeline, true, now, 15))
    }
    @Test fun `disconnect makes readings unavailable ignores packets and reconnect starts a new session`() {
        val history = history(); history.startSession("A"); history.record(sensor, "1")
        val first = history.publish(); history.endSession(); now++; history.record(sensor, "2")
        val ended = history.publish()
        assertFalse(ended.active)
        assertEquals(first.sensors, ended.sensors)
        assertEquals(ReadingFreshness.UNAVAILABLE, readingFreshness(ended.sensors[source], ended.active, now, 15))
        history.startSession("A")
        assertTrue(history.state.value.sensors.isEmpty())
        assertNotEquals(first.sessionId, history.state.value.sessionId)
        history.record(sensor, "3")
        assertEquals(1, history.publish().sensors[source]!!.samples.size)
    }
    @Test fun `freshness uses the last successful sensor update and configurable monotonic timeout`() {
        val timeline = SensorTimeline(lastSuccessfulMs = 1000, latestValid = true)
        assertEquals(ReadingFreshness.LIVE, readingFreshness(timeline, true, 16000, 15))
        assertEquals(ReadingFreshness.STALE, readingFreshness(timeline, true, 16001, 15))
        assertEquals(ReadingFreshness.LIVE, readingFreshness(timeline, true, 16001, 30))
        assertEquals(ReadingFreshness.UNAVAILABLE, readingFreshness(timeline, true, 999, 15))
    }
}
