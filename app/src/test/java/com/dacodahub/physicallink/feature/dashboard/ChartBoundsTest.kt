package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import org.junit.*
import org.junit.Assert.*

class ChartBoundsTest {
    private val samples = listOf(NumericSample(0, 1.0, 0), NumericSample(1000, 2.0, 0), NumericSample(2000, 3.0, 0))
    private val series get() = chartSeries(samples, 2000, 60, 15000)!!
    @Test fun `one sided bounds derive a finite nonzero opposite span even outside the data`() {
        for (settings in listOf(WidgetSettings(), WidgetSettings(chartMinimum = 10.0), WidgetSettings(chartMaximum = -10.0),
            WidgetSettings(chartMinimum = -Double.MAX_VALUE), WidgetSettings(chartMaximum = Double.MAX_VALUE),
            WidgetSettings(chartMinimum = -5.0, chartMaximum = 5.0))) {
            val bounded = withChartBounds(series, settings)
            assertTrue(bounded.minimum.isFinite() && bounded.maximum.isFinite() && bounded.minimum < bounded.maximum)
            settings.chartMinimum?.let { assertEquals(it, bounded.minimum, 0.0) }
            settings.chartMaximum?.let { assertEquals(it, bounded.maximum, 0.0) }
            assertEquals(samples, bounded.segments.flatten())
        }
    }
    @Test fun `out of range curves disappear rather than becoming false baselines and crossings are clipped`() {
        assertTrue(chartLines(withChartBounds(series, WidgetSettings(chartMinimum = 10.0)), 2000, 60).isEmpty())
        assertTrue(chartLines(withChartBounds(series, WidgetSettings(chartMaximum = -10.0)), 2000, 60).isEmpty())
        val clipped = withChartBounds(series, WidgetSettings(chartMinimum = 1.5, chartMaximum = 2.5))
        val lines = chartLines(clipped, 2000, 60)
        assertEquals(2, lines.size)
        assertEquals(0f, lines.first().startY, 0f)
        assertEquals(1f, lines.last().endY, 0f)
        assertTrue(lines.all { it.startX.isFinite() && it.endX.isFinite() && it.startY in 0f..1f && it.endY in 0f..1f })
        assertEquals(samples, clipped.segments.flatten())
    }
    @Test fun `flat zero tiny and extreme readings preserve valid axes and never change samples`() {
        for (value in listOf(0.0, Double.MIN_VALUE, -Double.MIN_VALUE, 1e-300, -1e-300, Double.MAX_VALUE, -Double.MAX_VALUE)) {
            val sample = NumericSample(0, value, 0)
            val flat = chartSeries(listOf(sample), 0, 60, 15000)!!
            assertTrue(flat.minimum.isFinite() && flat.maximum.isFinite() && flat.minimum < flat.maximum)
            assertEquals(sample, flat.segments.single().single())
        }
        val close = series.copy(minimum = 1.0000001, maximum = 1.0000002)
        val labels = chartAxisLabels(close, 0)
        assertNotEquals(labels.first, labels.second)
    }
    @Test fun `clipping extreme finite values never overflows or bridges history gaps`() {
        val points = listOf(NumericSample(0, -Double.MAX_VALUE, 0), NumericSample(1000, Double.MAX_VALUE, 0), NumericSample(2000, 0.0, 1))
        val bounded = withChartBounds(chartSeries(points, 2000, 60, 15000)!!, WidgetSettings(chartMinimum = -1.0, chartMaximum = 1.0))
        assertEquals(1, chartLines(bounded, 2000, 60).size)
        assertTrue(chartLines(bounded, 2000, 60).all { it.startX.isFinite() && it.endX.isFinite() })
    }
}
