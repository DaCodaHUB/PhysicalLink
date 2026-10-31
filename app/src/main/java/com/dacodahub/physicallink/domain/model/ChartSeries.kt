package com.dacodahub.physicallink.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.ceil

data class ChartSeries(val segments: List<List<NumericSample>>, val minimum: Double, val maximum: Double, val count: Int)

/** Geometry uses real samples only; disjoint segments are downsampled independently. */
fun chartSeries(samples: List<NumericSample>, nowMs: Long, windowSeconds: Int, gapMs: Long): ChartSeries? {
    val cutoff = nowMs - windowSeconds.toLong() * 1000
    val visible = samples.filter { it.timeMs in cutoff..nowMs && it.value.isFinite() }
    if (visible.isEmpty()) return null
    val segments = mutableListOf<MutableList<NumericSample>>()
    visible.forEach { sample ->
        val previous = segments.lastOrNull()?.lastOrNull()
        if (previous == null || sample.segment != previous.segment || sample.timeMs - previous.timeMs > gapMs)
            segments.add(mutableListOf())
        segments.last().add(sample)
    }
    var low = visible.minOf { it.value }
    var high = visible.maxOf { it.value }
    if (low == high) {
        val padding = if (low == 0.0) 0.000001 else max(abs(low) * 0.05, Math.ulp(low))
        low = (low - padding).takeIf { it.isFinite() } ?: low
        high = (high + padding).takeIf { it.isFinite() } ?: high
    }
    val recentSegments = segments.takeLast(150)
    val total = recentSegments.sumOf { it.size }.coerceAtLeast(1)
    val reduced = recentSegments.map { segment ->
        val budget = max(2, 300 * segment.size / total)
        if (segment.size <= budget) segment.toList() else {
            val chunkSize = ceil(segment.size.toDouble() / max(1, budget / 2)).toInt()
            val picked = mutableListOf(segment.first())
            segment.chunked(chunkSize).forEach { chunk ->
                val extrema = listOf(chunk.minBy { it.value }, chunk.maxBy { it.value }).sortedBy { it.timeMs }
                picked.addAll(extrema)
            }
            picked.add(segment.last())
            picked.distinct().sortedBy { it.timeMs }
        }
    }
    return ChartSeries(reduced, low, high, visible.size)
}

/** Scaling first avoids overflow for finite values spanning both Double extremes. */
fun axisFraction(value: Double, minimum: Double, maximum: Double): Float {
    val scale = max(abs(minimum), abs(maximum)).takeIf { it > 0 && it.isFinite() } ?: 1.0
    return ((value / scale - minimum / scale) / (maximum / scale - minimum / scale)).toFloat().coerceIn(0f, 1f)
}
