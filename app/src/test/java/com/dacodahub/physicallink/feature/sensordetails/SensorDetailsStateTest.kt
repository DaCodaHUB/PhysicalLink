package com.dacodahub.physicallink.feature.sensordetails

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.data.repository.SessionSensorHistory
import com.dacodahub.physicallink.feature.dashboard.*
import java.math.BigDecimal
import org.junit.*
import org.junit.Assert.*

class SensorDetailsStateTest {
    private val manifest = SensorManifest("12345678-1234-1234-1234-123456789abc", "Project", "0000000000000001",
        listOf(ManifestSensor("arbitrary.id", "Long sensor \u0394 \u6E2C\u5B9A", "\u03BCg/(m\u00B2\u00B7s)")))
    private val source = SensorSource(manifest.dashboardDeviceId(), "arbitrary.id")
    private fun input() = SensorDetailsInput(source.deviceId, manifest.dashboardCapabilities(),
        SensorHistorySnapshot(source.deviceId, 1, true, mapOf(source to SensorTimeline(listOf(NumericSample(1000, -2.0, 0), NumericSample(2000, 4.0, 0)), 2000, 4.0, true))),
        ConnectionState.Connected, mapOf(source.capabilityId to "4"),
        LiveSensorState(mapOf(source.capabilityId to SensorReading(9, 4.0, 3, 1000, 3000)), true), manifest, 4000)
    @Test fun `stable identity resolves metadata independent of channel ordering and acquisition age advances`() {
        val state = sensorDetailsState(source, input())
        assertEquals(manifest.sensors.single().name, state.reading.capability!!.name)
        assertEquals("\u03BCg/(m\u00B2\u00B7s)", state.reading.capability.unit)
        assertEquals(2000L, state.acquisitionAgeMs)
        assertEquals("Live", state.status)
        assertEquals(2, state.statistics!!.count)
        assertEquals("Stale - last known reading", sensorDetailsState(source, input().copy(nowMs = 32000)).status)
        assertEquals("Last known: 4 \u03BCg/(m\u00B2\u00B7s)", chartCurrentValue(sensorDetailsState(source, input().copy(nowMs = 32000)).reading))
        val unavailable = input().copy(live = LiveSensorState(mapOf(source.capabilityId to SensorReading(0, null, 4, 0xffffffffL, 4000)), true))
        assertEquals("Unavailable", sensorDetailsState(source, unavailable).status)
    }
    @Test fun `statistics filter invalid future and expired samples and never use chart reduction`() {
        val points = listOf(NumericSample(-1, 1000.0, 0), NumericSample(0, -4.0, 0), NumericSample(30000, 2.0, 1),
            NumericSample(60000, 8.0, 1), NumericSample(60001, 999.0, 1), NumericSample(40000, Double.NaN, 1), NumericSample(50000, Double.POSITIVE_INFINITY, 1))
        val stats = historyStatistics(points, 60000, 60)!!
        assertEquals(3, stats.count); assertEquals(-4.0, stats.minimum, 0.0); assertEquals(8.0, stats.maximum, 0.0)
        assertEquals(0, BigDecimal("2").compareTo(stats.average))
        assertEquals(0, stats.firstMs); assertEquals(60000, stats.lastMs)
        assertNull(historyStatistics(emptyList(), 60000, 60))
        assertNull(historyStatistics(listOf(NumericSample(0, Double.NEGATIVE_INFINITY, 0)), 60000, 60))
        val dense = (0..1000).map { NumericSample(it.toLong(), it.toDouble(), 0) }
        assertEquals(1001, historyStatistics(dense, 1000, 60)!!.count)
    }
    @Test fun `averages do not overflow and tiny negative values retain meaning`() {
        val large = listOf(NumericSample(0, Double.MAX_VALUE, 0), NumericSample(1, Double.MAX_VALUE, 0))
        assertTrue(historyStatistics(large, 1, 60)!!.average.toDouble().isFinite())
        val cancel = listOf(NumericSample(0, Double.MAX_VALUE, 0), NumericSample(1, -Double.MAX_VALUE, 0))
        assertEquals(0, historyStatistics(cancel, 1, 60)!!.average.compareTo(BigDecimal.ZERO))
        val tiny = historyStatistics(listOf(NumericSample(0, -8.4e-8, 0)), 1, 60)!!
        assertEquals("-8.4E-8", formatSensorValue(tiny.average.toString(), "number", 3))
    }
    @Test fun `missing disconnected and different devices never become current`() {
        val missing = sensorDetailsState(source.copy(capabilityId = "removed"), input())
        assertTrue(missing.missing); assertNull(missing.statistics)
        val other = sensorDetailsState(source, input().copy(deviceId = "another", history = input().history.copy(deviceId = "another")))
        assertTrue(other.missing); assertNull(other.statistics); assertNull(readingForDisplay(other.reading))
        val disconnected = sensorDetailsState(source, input().copy(connection = ConnectionState.ConnectionLost, history = input().history.copy(active = false)))
        assertEquals("Disconnected", disconnected.status)
        assertNull(readingForDisplay(disconnected.reading))
        assertEquals(2, disconnected.statistics!!.count) // explicitly retained historical measurements, never current.
    }
    @Test fun `new epochs and sessions clear history and removed metadata cannot expose old samples`() {
        var now = 1000L
        val store = SessionSensorHistory(MonotonicClock { now })
        store.startSession(source.deviceId)
        store.record(manifest.dashboardCapabilities().capabilities.single(), "4")
        val old = store.publish()
        assertEquals(1, sensorDetailsState(source, input().copy(history = old, nowMs = now)).statistics!!.count)
        store.endSession(); now += 1000
        store.startSession(source.deviceId)
        val new = store.publish()
        assertTrue(new.sessionId > old.sessionId)
        val changed = sensorDetailsState(source, input().copy(manifest = manifest.copy(epoch = "0000000000000002"), history = new, live = LiveSensorState(), values = emptyMap(), nowMs = now))
        assertNull(changed.statistics); assertNull(readingForDisplay(changed.reading))
        val removed = input().copy(metadata = manifest.copy(sensors = emptyList()).dashboardCapabilities())
        assertNull(sensorDetailsState(source, removed).statistics)
    }
    @Test fun `epoch metadata arriving before a history reset cannot expose previous epoch samples`() {
        val original = input()
        val gate = SensorDetailsSessionGate()
        assertEquals(2, sensorDetailsState(source, gate.accept(original)).statistics!!.count)
        val changed = original.copy(manifest = manifest.copy(epoch = "0000000000000002"))
        val held = sensorDetailsState(source, gate.accept(changed))
        assertNull(held.statistics)
        assertNull(readingForDisplay(held.reading))
        assertEquals(2, original.history.sensors.getValue(source).samples.size)
        val reset = changed.copy(history = changed.history.copy(sessionId = 2, sensors = emptyMap()), live = LiveSensorState(), values = emptyMap())
        assertNull(sensorDetailsState(source, gate.accept(reset)).statistics)
        val next = reset.copy(history = reset.history.copy(sensors = mapOf(source to SensorTimeline(
            listOf(NumericSample(4000, 7.0, 0)), 4000, 7.0, true))), values = mapOf(source.capabilityId to "7"))
        assertEquals(1, sensorDetailsState(source, gate.accept(next)).statistics!!.count)
    }
    @Test fun `a newer validated live record updates value and age without inventing history samples`() {
        val next = input().copy(nowMs = 5000, values = mapOf(source.capabilityId to "7"),
            live = LiveSensorState(mapOf(source.capabilityId to SensorReading(0, 7.0, 4, 500, 4500)), true))
        val details = sensorDetailsState(source, next)
        assertEquals("7", readingForDisplay(details.reading))
        assertEquals(1000L, details.acquisitionAgeMs)
        assertEquals(2, details.statistics!!.count)
        assertEquals(4.0, details.statistics.maximum, 0.0)
        assertEquals(2, next.history.sensors.getValue(source).samples.size)
    }
}
