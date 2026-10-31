package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

internal fun axisAbove(value: Double): Double {
    val pad = if (value == 0.0) 0.000001 else maxOf(abs(value) * 0.05, Math.ulp(value))
    val result = value + pad
    return if (result.isFinite() && result > value) result else Math.nextUp(value)
}
internal fun axisBelow(value: Double): Double {
    val pad = if (value == 0.0) 0.000001 else maxOf(abs(value) * 0.05, Math.ulp(value))
    val result = value - pad
    return if (result.isFinite() && result < value) result else Math.nextDown(value)
}
internal fun withChartBounds(series: ChartSeries, settings: WidgetSettings): ChartSeries {
    require(settings.validationError() == null)
    val low = settings.chartMinimum ?: if (settings.chartMaximum != null && series.minimum >= settings.chartMaximum) axisBelow(settings.chartMaximum) else series.minimum
    val high = settings.chartMaximum ?: if (settings.chartMinimum != null && series.maximum <= settings.chartMinimum) axisAbove(settings.chartMinimum) else series.maximum
    require(low.isFinite() && high.isFinite() && low < high)
    return series.copy(minimum = low, maximum = high)
}

/** Display geometry only. Clip segments at the axis, never clamp raw samples into a false baseline. */
internal data class ChartLine(val startX: Float, val startY: Float, val endX: Float, val endY: Float)
internal fun chartLines(series: ChartSeries, nowMs: Long, windowSeconds: Int): List<ChartLine> = buildList {
    val low = series.minimum; val high = series.maximum
    fun crossing(bound: Double, from: Double, to: Double): Double =
        (BigDecimal.valueOf(bound) - BigDecimal.valueOf(from)).divide(BigDecimal.valueOf(to) - BigDecimal.valueOf(from), MathContext.DECIMAL128).toDouble().coerceIn(0.0, 1.0)
    series.segments.forEach { segment ->
        segment.zipWithNext().forEach pairs@ { (a, b) ->
            if ((a.value < low && b.value < low) || (a.value > high && b.value > high)) return@pairs
            val aInside = a.value in low..high
            val bInside = b.value in low..high
            val enter = if (aInside) 0.0 else crossing(if (a.value < low) low else high, a.value, b.value)
            val exit = if (bInside) 1.0 else crossing(if (b.value < low) low else high, a.value, b.value)
            if (enter > exit) return@pairs
            val x0 = chartTimeFraction(a.timeMs, nowMs, windowSeconds)
            val x1 = chartTimeFraction(b.timeMs, nowMs, windowSeconds)
            fun x(t: Double) = (x0 + (x1 - x0) * t).toFloat()
            val y0 = if (aInside) axisFraction(a.value, low, high) else if (a.value < low) 0f else 1f
            val y1 = if (bInside) axisFraction(b.value, low, high) else if (b.value < low) 0f else 1f
            add(ChartLine(x(enter), y0, x(exit), y1))
        }
    }
}
