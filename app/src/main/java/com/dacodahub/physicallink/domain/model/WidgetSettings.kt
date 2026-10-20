package com.dacodahub.physicallink.domain.model

data class WidgetSettings(
    val chartWindowSeconds: Int = 60,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val lowerThreshold: Double? = null,
    val upperThreshold: Double? = null,
    val precision: Int = 2,
    val staleAfterSeconds: Int = 15
) {
    fun validationError(): String? = when {
        chartWindowSeconds !in listOf(60, 300, 900) -> "Choose a supported chart window."
        precision !in 0..6 -> "Precision must be between 0 and 6."
        staleAfterSeconds !in 1..3600 -> "Freshness must be between 1 and 3600 seconds."
        (minimum == null) != (maximum == null) -> "Set both minimum and maximum."
        minimum != null && maximum != null && (!minimum.isFinite() || !maximum.isFinite() ||
            minimum >= maximum || !(maximum - minimum).isFinite()) -> "Minimum must be less than maximum, with a finite range."
        lowerThreshold != null && (!lowerThreshold.isFinite() || minimum == null || maximum == null || lowerThreshold !in minimum..maximum) -> "Low threshold must be inside the range."
        upperThreshold != null && (!upperThreshold.isFinite() || minimum == null || maximum == null || upperThreshold !in minimum..maximum) -> "High threshold must be inside the range."
        lowerThreshold != null && upperThreshold != null && lowerThreshold > upperThreshold -> "Low threshold must not exceed high threshold."
        else -> null
    }
    val hasRange: Boolean get() = minimum != null && maximum != null && validationError() == null
}

enum class BoundStatus { BELOW, INSIDE, ABOVE }
data class RangeReading(val fraction: Float, val status: BoundStatus)
fun rangeReading(value: Double?, settings: WidgetSettings): RangeReading? {
    if (value == null || !value.isFinite() || !settings.hasRange) return null
    val min = settings.minimum!!
    val max = settings.maximum!!
    return RangeReading(((value.coerceIn(min, max) - min) / (max - min)).toFloat(), when {
        value < min -> BoundStatus.BELOW
        value > max -> BoundStatus.ABOVE
        else -> BoundStatus.INSIDE
    })
}
