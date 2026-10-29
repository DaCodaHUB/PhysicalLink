package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.data.repository.SessionSensorHistory
import org.junit.Test
import org.junit.Assert.*

class V2DashboardIntegrationTest {
    private fun manifest(count: Int = 1, epoch: String = "0000000000000001") = V2Metadata(
        "00112233-4455-6677-8899-aabbccddeeff", "Board", epoch,
        (1..count).map { V2Sensor("sensor-$it", "Probe $it", "units") })

    @Test fun `one and sixteen sensors automatically produce named Value widgets`() {
        listOf(1, 16).forEach { count ->
            val m = manifest(count)
            val state = dashboardState(m.dashboardDeviceId(), m.dashboardCapabilities(), emptyMap(), null)
            assertEquals(count, state.widgets.size)
            assertTrue(state.widgets.all { it.widget.type == WidgetType.VALUE && it.value == null })
            assertEquals(m.sensors.map { it.id }, state.widgets.map { it.widget.source.capabilityId })
            assertEquals(m.sensors.map { it.name }, state.widgets.map { it.capability?.name })
        }
    }
    @Test fun `reorder missing new and duplicate widgets preserve customization`() {
        val m = manifest(3)
        val id = m.dashboardDeviceId()
        val original = defaultDashboard(id, m.dashboardCapabilities())
            .duplicateWidget(defaultDashboard(id, m.dashboardCapabilities()).widgets.first().id, "copy")
        val changed = m.copy(sensors = listOf(m.sensors[2], m.sensors[0], V2Sensor("new", "New", "")))
        val reconciled = original.withDiscoveredSensors(id, changed.dashboardCapabilities())
        assertEquals(original.widgets, reconciled.widgets.take(original.widgets.size))
        assertEquals("new", reconciled.widgets.last().source.capabilityId)
        val state = dashboardState(id, changed.dashboardCapabilities(), emptyMap(), reconciled)
        assertNull(state.widgets.first { it.widget.source.capabilityId == "sensor-2" }.capability)
        assertEquals(2, state.widgets.count { it.widget.source.capabilityId == "sensor-1" })
        assertEquals(id, manifest(epoch = "0000000000000002").dashboardDeviceId())
    }
    @Test fun `history uses acquisition age deduplicates images and isolates reconnects`() {
        var now = 100_000L
        val m = manifest()
        val history = SessionSensorHistory(MonotonicClock { now })
        val source = SensorSource(m.dashboardDeviceId(), "sensor-1")
        val capability = m.dashboardCapabilities().capabilities.single()
        history.startSession(source.deviceId)
        var updates = V2DashboardUpdates()
        fun ingest(sequence: Long, value: Double?, age: Long = 0) {
            updates.accept(V2LiveState(mapOf(source.capabilityId to V2Reading(0, value, sequence, age, now)), true)) { _, r ->
                history.record(capability, r.value?.toString().orEmpty(), r.receivedAtMs - if (r.value == null) 0 else r.ageMs)
            }
            history.publish()
        }
        ingest(1, 4.0, 40_000)
        assertEquals(ReadingFreshness.STALE, readingFreshness(history.state.value.sensors[source], true, now, 30))
        ingest(1, 4.0); ingest(0, 2.0)
        assertEquals(1, history.state.value.sensors[source]!!.samples.size)
        now += 1000; ingest(2, 5.0)
        assertEquals(2, history.state.value.sensors[source]!!.samples.size)
        assertEquals(ReadingFreshness.LIVE, readingFreshness(history.state.value.sensors[source], true, now, 30))
        ingest(3, null)
        assertFalse(history.state.value.sensors[source]!!.latestValid)
        history.endSession()
        assertFalse(history.state.value.active)
        history.startSession(source.deviceId)
        updates = V2DashboardUpdates()
        assertTrue(history.state.value.sensors.isEmpty())
        ingest(1, 8.0)
        assertEquals(1, history.state.value.sensors[source]!!.samples.size)
        history.startSession("v2:other")
        assertTrue(history.state.value.sensors.isEmpty())
    }
    @Test fun `sixteen simultaneous numeric updates feed the existing chart series`() {
        var now = 10_000L
        val m = manifest(16)
        val history = SessionSensorHistory(MonotonicClock { now }, maxSamples = 4)
        history.startSession(m.dashboardDeviceId())
        val updates = V2DashboardUpdates()
        repeat(8) { sequence ->
            now += 1000
            val readings = m.sensors.mapIndexed { channel, sensor ->
                sensor.id to V2Reading(channel, channel + sequence.toDouble(), sequence.toLong(), 0, now)
            }.toMap()
            updates.accept(V2LiveState(readings, true)) { id, reading ->
                history.record(m.dashboardCapabilities().capabilities.first { it.id == id }, reading.value.toString(), now)
            }
        }
        val state = dashboardState(m.dashboardDeviceId(), m.dashboardCapabilities(), emptyMap(), null, history.publish(), now)
        assertEquals(16, state.widgets.size)
        state.widgets.forEach {
            assertEquals(4, it.timeline!!.samples.size)
            assertEquals(4, chartSeries(it.timeline.samples, now, 60, 15000)!!.count)
            assertEquals(ReadingFreshness.LIVE, it.freshness)
        }
    }

    @Test fun `v1 identity and default widgets remain unchanged`() {
        val c = DeviceCapabilities(1, "V1", listOf(DeviceCapability("temperature", "uuid", "C", "number")))
        val layout = defaultDashboard("AA:BB", c)
        assertEquals(SensorSource("AA:BB", "temperature"), layout.widgets.single().source)
        assertEquals("default:5:AA:BB:temperature", layout.widgets.single().id)
        assertNotEquals("AA:BB", manifest().dashboardDeviceId())
    }
}
