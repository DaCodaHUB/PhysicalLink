package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ChartPresentationTest {
    private fun state(samples: List<NumericSample>) = DashboardWidgetState(
        DashboardWidget("chart", WidgetType.CHART, SensorSource("board", "temperature")),
        DeviceCapability("temperature", "5678", "C", "number"), "12", SensorTimeline(samples, 2000, 12.0, true), ReadingFreshness.LIVE, 2000)
    @Test fun `chart summary exposes current unit freshness and trend only within latest segment`() {
        val samples = listOf(NumericSample(0, 100.0, 0), NumericSample(1000, 10.0, 1), NumericSample(2000, 12.0, 1))
        val summary = chartSummary(state(samples), chartSeries(samples, 2000, 60, 15000)!!)
        assertTrue(summary.contains("Current: 12 \u00b0C"))
        assertTrue(summary.contains("rose from 10 to 12"))
        assertFalse(summary.contains("fell from 100"))
        assertTrue(summary.contains("trend does not cross gaps"))
        assertTrue(summary.contains("Live"))
    }
    @Test fun `single sample flat and unavailable summaries do not invent trends or values`() {
        val single = listOf(NumericSample(2000, 12.0, 0))
        assertTrue(chartSummary(state(single), chartSeries(single, 2000, 60, 15000)!!).contains("Trend unavailable"))
        val flat = listOf(NumericSample(0, 12.0, 0), single.single())
        assertTrue(chartSummary(state(flat), chartSeries(flat, 2000, 60, 15000)!!).contains("was unchanged"))
        assertEquals("Current value unavailable", chartCurrentValue(state(flat).copy(timeline = SensorTimeline())))
    }
    @Test fun `rapid unrelated updates reuse plot time buckets without hiding a new history sample`() {
        val samples = listOf(NumericSample(1000, 12.0, 0))
        val times = (1000L..1999L).map { chartRenderTime(it, samples) }.toSet()
        assertEquals(4, times.size)
        val newer = samples + NumericSample(1265, 13.0, 0)
        assertEquals(1265, chartRenderTime(1270, newer))
        assertEquals(2, chartSeries(newer, chartRenderTime(1270, newer), 60, 15000)!!.count)
    }
    @Test fun `flat near zero chart axes stay distinct for visual and spoken labels`() {
        val samples = listOf(NumericSample(2000, 0.0, 0))
        val series = chartSeries(samples, 2000, 60, 15000)!!
        val axes = chartAxisLabels(series, 2)
        assertNotEquals(axes.first, axes.second)
        val summary = chartSummary(state(samples), series)
        assertTrue(summary.contains("Value axis ${axes.first} to ${axes.second}"))
    }

}
