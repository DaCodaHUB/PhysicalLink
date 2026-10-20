package com.dacodahub.physicallink.domain.model

fun interface MonotonicClock { fun nowMs(): Long }
data class NumericSample(val timeMs: Long, val value: Double, val segment: Long)
data class SensorTimeline(
    val samples: List<NumericSample> = emptyList(),
    val lastSuccessfulMs: Long? = null,
    val latestNumeric: Double? = null,
    val latestValid: Boolean = false
)
data class SensorHistorySnapshot(
    val deviceId: String? = null, val sessionId: Long = 0, val active: Boolean = false,
    val sensors: Map<SensorSource, SensorTimeline> = emptyMap()
)
enum class ReadingFreshness { LIVE, STALE, UNAVAILABLE }
fun readingFreshness(timeline: SensorTimeline?, active: Boolean, nowMs: Long, staleAfterSeconds: Int): ReadingFreshness {
    val updated = timeline?.lastSuccessfulMs
    if (!active || timeline?.latestValid != true || updated == null || nowMs < updated) return ReadingFreshness.UNAVAILABLE
    return if (nowMs - updated > staleAfterSeconds.toLong() * 1000) ReadingFreshness.STALE else ReadingFreshness.LIVE
}
