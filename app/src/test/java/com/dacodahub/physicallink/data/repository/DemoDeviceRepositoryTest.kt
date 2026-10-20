package com.dacodahub.physicallink.data.repository

import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.dashboardState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoDeviceRepositoryTest {
    @Test fun `demo capabilities generate four numeric dashboard sources with explicit units`() {
        val demo = DemoDeviceRepository(MonotonicClock { 0 })
        assertEquals("Demo Sensor Station", demo.capabilities.value!!.device)
        assertEquals(listOf("\u00b0C", "%", "hPa", "%"), demo.capabilities.value!!.capabilities.map { it.unit })
        assertTrue(demo.capabilities.value!!.capabilities.all { it.type == "number" })
        val layout = defaultDashboard(DemoDeviceRepository.DEVICE_ID, demo.capabilities.value)
        assertEquals(4, layout.widgets.size)
        assertTrue(layout.widgets.all { it.source.deviceId == DemoDeviceRepository.DEVICE_ID })
        assertEquals(ScanState.Idle, demo.scanState.value)
        assertTrue(demo.sensorValues.value.isEmpty())
    }

    @Test fun `simulation updates once per second with smooth finite bounded readings and bounded history`() = runTest {
        val demo = DemoDeviceRepository(MonotonicClock { testScheduler.currentTime })
        val job = backgroundScope.launch { demo.simulateWhileVisible() }
        runCurrent()
        val first = demo.sensorValues.value
        advanceTimeBy(999); runCurrent()
        assertSame(first, demo.sensorValues.value)
        advanceTimeBy(1); runCurrent()
        assertNotEquals(first, demo.sensorValues.value)
        val bounds = listOf(18.0..30.0, 30.0..75.0, 990.0..1030.0, 20.0..85.0)
        var previous = demo.sensorValues.value
        repeat(2000) {
            advanceTimeBy(1000); runCurrent()
            DemoDeviceRepository.CAPABILITIES.capabilities.forEachIndexed { index, sensor ->
                val value = demo.sensorValues.value.getValue(sensor.id).toDouble()
                assertTrue(value.isFinite() && value in bounds[index])
                assertTrue(kotlin.math.abs(value - previous.getValue(sensor.id).toDouble()) < 2)
            }
            previous = demo.sensorValues.value
        }
        assertEquals(4, demo.sensorHistory.value.sensors.size)
        assertTrue(demo.sensorHistory.value.sensors.values.all { it.samples.size <= 901 })
        job.cancelAndJoin()
    }

    @Test fun `cancellation stops all production and marks samples unavailable without deleting layout state`() = runTest {
        val demo = DemoDeviceRepository(MonotonicClock { testScheduler.currentTime })
        val job = backgroundScope.launch { demo.simulateWhileVisible() }; runCurrent()
        job.cancelAndJoin()
        val last = demo.sensorValues.value
        val stopped = demo.sensorHistory.value
        assertFalse(stopped.active)
        advanceTimeBy(60000); runCurrent()
        assertSame(last, demo.sensorValues.value)
        assertSame(stopped, demo.sensorHistory.value)
        assertTrue(stopped.sensors.values.all { readingFreshness(it, stopped.active, testScheduler.currentTime, 15) == ReadingFreshness.UNAVAILABLE })
    }

    @Test fun `rapid enter exit creates one session at a time with no old samples after resume`() = runTest {
        val demo = DemoDeviceRepository(MonotonicClock { testScheduler.currentTime })
        var previousSession = 0L
        repeat(30) {
            val job = backgroundScope.launch { demo.simulateWhileVisible() }; runCurrent()
            val snapshot = demo.sensorHistory.value
            assertTrue(snapshot.active)
            assertTrue(snapshot.sessionId > previousSession)
            assertTrue(snapshot.sensors.values.all { it.samples.size == 1 })
            previousSession = snapshot.sessionId
            advanceTimeBy(250); job.cancelAndJoin()
            assertFalse(demo.sensorHistory.value.active)
        }
        val final = demo.sensorValues.value
        advanceTimeBy(10000); runCurrent()
        assertSame(final, demo.sensorValues.value)
    }

    @Test fun `all visualizations consume the same history and independent widget settings`() = runTest {
        val demo = DemoDeviceRepository(MonotonicClock { testScheduler.currentTime })
        val job = backgroundScope.launch { demo.simulateWhileVisible() }; runCurrent()
        advanceTimeBy(1000); runCurrent()
        val source = SensorSource(DemoDeviceRepository.DEVICE_ID, "temperature")
        val settings = WidgetSettings(minimum = 18.0, maximum = 30.0)
        val layout = DashboardLayout(WidgetType.entries.map { DashboardWidget(it.name, it, source, settings = settings) })
        val state = dashboardState(DemoDeviceRepository.DEVICE_ID, demo.capabilities.value, demo.sensorValues.value,
            layout, demo.sensorHistory.value, testScheduler.currentTime)
        assertEquals(4, state.widgets.size)
        assertTrue(state.widgets.all { it.freshness == ReadingFreshness.LIVE && it.value?.toDoubleOrNull() != null })
        assertTrue(state.widgets.all { it.timeline === state.widgets.first().timeline })
        val timeline = state.widgets.first().timeline!!
        assertEquals(2, chartSeries(timeline.samples, testScheduler.currentTime, 60, 15000)!!.count)
        assertEquals(BoundStatus.INSIDE, rangeReading(timeline.latestNumeric, settings)!!.status)
        job.cancelAndJoin()
    }
}
