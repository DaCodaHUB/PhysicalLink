package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.core.designsystem.theme.currentWidgetAccent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.*

private fun number(value: Double, precision: Int) = formatSensorValue(value.toString(), "number", precision)

@Composable
internal fun ChartWidget(state: DashboardWidgetState, controls: WidgetControls? = null) {
    val settings = state.widget.settings
    val samples = state.timeline?.samples.orEmpty()
    val plotTime = chartRenderTime(state.nowMs, samples)
    val series = remember(samples, plotTime, settings.chartWindowSeconds, settings.staleAfterSeconds) {
        chartSeries(samples, plotTime, settings.chartWindowSeconds, settings.staleAfterSeconds * 1000L)
    }
    val axes = remember(series?.minimum, series?.maximum, settings.precision) {
        series?.let { chartAxisLabels(it, settings.precision) }
    }
    val unit = sensorUnit(state.capability?.unit.orEmpty())
    val ink = currentWidgetAccent()
    val grid = MaterialTheme.colorScheme.outlineVariant
    WidgetContainer((state.capability?.name ?: sensorName(state.widget.source.capabilityId)), status = freshnessLabel(state), statusIsAlert = displayedFreshness(state) != ReadingFreshness.LIVE, controls = controls) {
        WidgetReading(state, controls)
        if (state.capability?.type?.equals("number", true) != true) Text("A numeric sensor is required for a chart.")
        else if (series == null) Text("Waiting for numeric samples in this time window.")
        else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (displayedFreshness(state) != ReadingFreshness.LIVE)
                    Text("Recorded history", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (controls == null) Text("${axes!!.second} $unit", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.fillMaxWidth().height(if (controls == null) 140.dp else 56.dp).semantics {
                contentDescription = chartSummary(state, series)
            }.drawWithCache {
                val inset = 3.dp.toPx().coerceAtMost(minOf(size.width, size.height) / 2)
                val plotWidth = (size.width - 2 * inset).coerceAtLeast(0f)
                val plotHeight = (size.height - 2 * inset).coerceAtLeast(0f)
                val paths = mutableListOf<Path>()
                val fills = mutableListOf<Path>()
                val fillBrush = Brush.verticalGradient(listOf(ink.copy(alpha = 0.14f), ink.copy(alpha = 0f)),
                    startY = inset, endY = inset + plotHeight.coerceAtLeast(1f))
                val dots = mutableListOf<Offset>()
                series.segments.forEach { segment ->
                    fun point(sample: NumericSample) = Offset(
                        inset + chartTimeFraction(sample.timeMs, plotTime, settings.chartWindowSeconds) * plotWidth,
                        inset + (1f - axisFraction(sample.value, series.minimum, series.maximum)) * plotHeight)
                    if (segment.size == 1) dots += point(segment.first())
                    else if (segment.isNotEmpty()) {
                        val line = Path().apply {
                            val start = point(segment.first()); moveTo(start.x, start.y)
                            for (index in 1 until segment.size) { val next = point(segment[index]); lineTo(next.x, next.y) }
                        }
                        paths += line
                        // Close each segment separately to preserve gaps between missing updates.
                        fills += Path().apply {
                            addPath(line)
                            lineTo(point(segment.last()).x, inset + plotHeight)
                            lineTo(point(segment.first()).x, inset + plotHeight)
                            close()
                        }
                    }
                }
                onDrawBehind {
                    fills.forEach { drawPath(it, fillBrush) }
                    repeat(3) { index ->
                        val y = inset + plotHeight * index / 2f
                        drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                    }
                    paths.forEach { drawPath(it, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
                    dots.forEach { drawCircle(ink, 3.dp.toPx(), it) }
                }
            })
            if (controls == null) {
                Text("${axes!!.first} $unit", style = MaterialTheme.typography.labelSmall)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${settings.chartWindowSeconds / 60} min ago", style = MaterialTheme.typography.labelSmall)
                    Text("Now", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

    }
}

@Composable
internal fun BoundedWidget(state: DashboardWidgetState, controls: WidgetControls? = null) {
    val settings = state.widget.settings
    val value = readingForDisplay(state)?.toDoubleOrNull()
    val range = rangeReading(value, settings)
    val unit = sensorUnit(state.capability?.unit.orEmpty())
    val ink = currentWidgetAccent()
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val error = MaterialTheme.colorScheme.error
    val title = (state.capability?.name ?: sensorName(state.widget.source.capabilityId))
    WidgetContainer(title, status = freshnessLabel(state), statusIsAlert = displayedFreshness(state) != ReadingFreshness.LIVE, controls = controls) {
        WidgetReading(state, controls)
        when {
            state.capability?.type?.equals("number", true) != true -> Text("A numeric sensor is required for this visualization.")
            !settings.hasRange -> Text("Set minimum and maximum in widget settings.")
            range == null -> Unit
            else -> {

                val outside = range.status != BoundStatus.INSIDE
                val description = when (range.status) {
                    BoundStatus.BELOW -> "Below configured minimum"
                    BoundStatus.ABOVE -> "Above configured maximum"
                    BoundStatus.INSIDE -> when {
                        settings.lowerThreshold != null && value!! < settings.lowerThreshold -> "Below lower threshold"
                        settings.upperThreshold != null && value!! > settings.upperThreshold -> "Above upper threshold"
                        else -> "Within configured bounds"
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gaugeHeight = minOf(if (controls == null) 96.dp else 48.dp, maxWidth / 2 + 8.dp)
                Canvas(Modifier.fillMaxWidth().height(if (state.widget.type == WidgetType.GAUGE) gaugeHeight else 32.dp)
                    .semantics { contentDescription = description }) {
                    val color = if (outside) error else ink
                    if (state.widget.type == WidgetType.GAUGE) {
                        val width = minOf(size.width - 12.dp.toPx(), (size.height - 8.dp.toPx()) * 2).coerceAtLeast(1f)
                        val origin = Offset((size.width - width) / 2, 6.dp.toPx())
                        val arcSize = Size(width, width)
                        drawArc(track, 180f, 180f, false, origin, arcSize, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                        if (range.fraction > 0) drawArc(color, 180f, 180 * range.fraction, false, origin, arcSize,
                            style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        val left = 4.dp.toPx(); val right = size.width - left; val y = size.height / 2
                        drawLine(track, Offset(left, y), Offset(right, y), 8.dp.toPx(), StrokeCap.Round)
                        drawLine(color, Offset(left, y), Offset(left + (right - left) * range.fraction, y), 8.dp.toPx(), StrokeCap.Round)
                        listOfNotNull(settings.lowerThreshold, settings.upperThreshold).forEach { threshold ->
                            val x = left + (right - left) * axisFraction(threshold, settings.minimum!!, settings.maximum!!)
                            drawLine(error, Offset(x, y - 10.dp.toPx()), Offset(x, y + 10.dp.toPx()), 2.dp.toPx())
                        }
                        drawCircle(color, 6.dp.toPx(), Offset(left + (right - left) * range.fraction, y))
                    }
                }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(number(settings.minimum!!, settings.precision), style = MaterialTheme.typography.labelSmall)
                    Text(number(settings.maximum!!, settings.precision), style = MaterialTheme.typography.labelSmall)
                }
                if (description != "Within configured bounds") Text(description, style = MaterialTheme.typography.bodySmall,
                    color = if (outside) error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (controls == null && (settings.lowerThreshold != null || settings.upperThreshold != null))
                    Text("Thresholds: ${settings.lowerThreshold?.let { number(it, settings.precision) } ?: "--"} / ${settings.upperThreshold?.let { number(it, settings.precision) } ?: "--"} $unit",
                        style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
