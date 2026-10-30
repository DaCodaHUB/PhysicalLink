package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.data.repository.SessionSensorHistory
import org.junit.Test
import org.junit.Assert.*

class DeviceDashboardIntegrationTest {
    private fun manifest(count: Int = 1, epoch: String = "0000000000000001") = SensorManifest(
        "00112233-4455-6677-8899-aabbccddeeff", "Board", epoch,
        (1..count).map { ManifestSensor("sensor-$it", "Probe $it", "units") })

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
        val changed = m.copy(sensors = listOf(m.sensors[2], m.sensors[0], ManifestSensor("new", "New", "")))
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
        var updates = DashboardUpdates()
        fun ingest(sequence: Long, value: Double?, age: Long = 0) {
            updates.accept(LiveSensorState(mapOf(source.capabilityId to SensorReading(0, value, sequence, age, now)), true)) { _, r ->
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
        updates = DashboardUpdates()
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
        val updates = DashboardUpdates()
        repeat(8) { sequence ->
            now += 1000
            val readings = m.sensors.mapIndexed { channel, sensor ->
                sensor.id to SensorReading(channel, channel + sequence.toDouble(), sequence.toLong(), 0, now)
            }.toMap()
            updates.accept(LiveSensorState(readings, true)) { id, reading ->
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

    @Test fun `existing storage identity and every widget survive a new firmware epoch`() {
        val m = manifest()
        val key = "v2:00112233-4455-6677-8899-aabbccddeeff"
        assertEquals(key, m.dashboardDeviceId())
        assertEquals("v2:sensor-1", m.dashboardCapabilities().capabilities.single().uuid)
        val layout = DashboardLayout(WidgetType.entries.mapIndexed { i, type ->
            DashboardWidget("saved-$i", type, SensorSource(key, "sensor-1"),
                if (type == WidgetType.CHART) 2 else 1, hidden = i == 1,
                settings = WidgetSettings(precision = 3, minimum = -10.0, maximum = 100.0))
        })
        val saved = com.dacodahub.physicallink.data.repository.DashboardLayoutCodec.encode(key, layout)
        val rebooted = manifest(epoch = "0000000000000002")
        val restored = com.dacodahub.physicallink.data.repository.DashboardLayoutCodec.decode(rebooted.dashboardDeviceId(), saved)
        assertEquals(layout, restored)
        assertEquals(layout, restored.withDiscoveredSensors(rebooted.dashboardDeviceId(), rebooted.dashboardCapabilities()))
    }
}
