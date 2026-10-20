package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VisualizationTest {
    private fun sample(time: Long, value: Double = 7.0, segment: Long = 0) = NumericSample(time, value, segment)
    @Test fun `chart empty single and flat readings have valid axes without fabricated samples`() {
        assertNull(chartSeries(emptyList(), 1000, 60, 15000))
        val single = chartSeries(listOf(sample(1000)), 1000, 60, 15000)!!
        assertEquals(listOf(listOf(sample(1000))), single.segments)
        assertTrue(single.minimum < 7 && single.maximum > 7)
        val flat = chartSeries(listOf(sample(1000), sample(2000)), 2000, 60, 15000)!!
        assertEquals(2, flat.count)
        assertEquals(0.5f, axisFraction(7.0, flat.minimum, flat.maximum), 0.001f)
    }
    @Test fun `time windows filter old future and nonfinite points`() {
        val points = listOf(sample(0), sample(300000), sample(840000), sample(900000), sample(900001), sample(900000, Double.NaN))
        assertEquals(2, chartSeries(points, 900000, 60, 15000)!!.count)
        assertEquals(2, chartSeries(points, 900000, 300, 15000)!!.count)
        assertEquals(4, chartSeries(points, 900000, 900, 15000)!!.count)
    }
    @Test fun `chart breaks on long gaps and invalid reading boundaries`() {
        val points = listOf(sample(0), sample(1000), sample(30000), sample(31000, segment = 1))
        val chart = chartSeries(points, 31000, 60, 15000)!!
        assertEquals(listOf(2, 1, 1), chart.segments.map { it.size })
    }
    @Test fun `dense charts retain extrema and bounded drawing points`() {
        val points = (0..10000).map { sample(it.toLong(), if (it == 5050) 999.0 else 7.0) }
        val chart = chartSeries(points, 10000, 60, 15000)!!
        assertEquals(10001, chart.count)
        assertEquals(999.0, chart.maximum, 0.0)
        assertTrue(chart.segments.flatten().size <= 604)
        assertTrue(chart.segments.flatten().any { it.value == 999.0 })
        assertEquals(0.5f, axisFraction(0.0, -Double.MAX_VALUE, Double.MAX_VALUE), 0.0f)
    }
    @Test fun `bounds are explicit finite ordered and thresholds validated`() {
        assertNull(rangeReading(20.0, WidgetSettings()))
        listOf(WidgetSettings(minimum = 1.0), WidgetSettings(minimum = 1.0, maximum = 1.0),
            WidgetSettings(minimum = Double.NaN, maximum = 2.0), WidgetSettings(precision = 7),
            WidgetSettings(minimum = 0.0, maximum = 10.0, lowerThreshold = -1.0),
            WidgetSettings(minimum = 0.0, maximum = 10.0, lowerThreshold = 8.0, upperThreshold = 2.0),
            WidgetSettings(staleAfterSeconds = 0)).forEach { assertNotNull(it.validationError()) }
        val settings = WidgetSettings(minimum = -20.0, maximum = 80.0, lowerThreshold = 0.0, upperThreshold = 60.0)
        assertNull(settings.validationError())
        assertEquals(RangeReading(0f, BoundStatus.BELOW), rangeReading(-21.0, settings))
        assertEquals(RangeReading(1f, BoundStatus.ABOVE), rangeReading(81.0, settings))
        assertEquals(RangeReading(0.5f, BoundStatus.INSIDE), rangeReading(30.0, settings))
        assertNull(rangeReading(Double.POSITIVE_INFINITY, settings))
    }
    @Test fun `configuration changes preserve identity source sizing visibility and ordering`() {
        val source = SensorSource("A", "temperature")
        val original = DashboardWidget("id", WidgetType.VALUE, source, 2, 2, true)
        val settings = WidgetSettings(300, -10.0, 40.0, 0.0, 30.0, 3, 60)
        val layout = DashboardLayout(listOf(original, original.copy(id = "duplicate")))
        val configured = layout.configureWidget("id", WidgetType.RANGE, settings)
        assertEquals(original.copy(type = WidgetType.RANGE, settings = settings), configured.widgets.first())
        assertEquals(layout.widgets.last(), configured.widgets.last())
        val duplicate = configured.duplicateWidget("id", "copy").widgets.last()
        assertEquals(configured.widgets.first().copy(id = "copy", hidden = false), duplicate)
        assertEquals("12.346", formatSensorValue("12.34567", "number", 3))
    }
    @Test fun `duplicate widgets share timeline while respecting individual freshness settings`() {
        val source = SensorSource("A", "temperature")
        val timeline = SensorTimeline(listOf(sample(0)), 0, 7.0, true)
        val history = SensorHistorySnapshot("A", 1, true, mapOf(source to timeline))
        val first = DashboardWidget("one", WidgetType.VALUE, source)
        val layout = DashboardLayout(listOf(first, first.copy(id = "two", type = WidgetType.CHART, settings = WidgetSettings(staleAfterSeconds = 60))))
        val metadata = DeviceCapabilities(1, "Board", listOf(DeviceCapability("temperature", "5678", "C", "number")))
        val state = dashboardState("A", metadata, mapOf("temperature" to "7"), layout, history, 20000, true)
        assertSame(state.widgets[0].timeline, state.widgets[1].timeline)
        assertEquals(ReadingFreshness.STALE, state.widgets[0].freshness)
        assertEquals(ReadingFreshness.LIVE, state.widgets[1].freshness)
        assertTrue(dashboardState("A", metadata, emptyMap(), layout, history, 20000, false).widgets.all { it.freshness == ReadingFreshness.UNAVAILABLE })
        assertTrue(dashboardState("B", metadata, emptyMap(), layout, history, 20000, true).widgets.all { it.timeline == null })
    }
}
