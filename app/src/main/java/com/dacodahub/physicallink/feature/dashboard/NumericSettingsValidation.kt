package com.dacodahub.physicallink.feature.dashboard

import com.dacodahub.physicallink.domain.model.*
import java.math.BigDecimal

internal enum class NumericField { MINIMUM, MAXIMUM, LOWER, UPPER, PRECISION, FRESHNESS, CHART_MINIMUM, CHART_MAXIMUM, GENERAL }
internal data class NumericSettingsDraft(
    val minimum: String, val maximum: String, val lower: String, val upper: String,
    val precision: String, val freshness: String, val window: Int,
    val chartMinimum: String, val chartMaximum: String, val autoMinimum: Boolean, val autoMaximum: Boolean
) {
    companion object {
        fun from(settings: WidgetSettings) = NumericSettingsDraft(settings.minimum?.toString().orEmpty(), settings.maximum?.toString().orEmpty(),
            settings.lowerThreshold?.toString().orEmpty(), settings.upperThreshold?.toString().orEmpty(),
            settings.precision.toString(), settings.staleAfterSeconds.toString(), settings.chartWindowSeconds,
            settings.chartMinimum?.toString().orEmpty(), settings.chartMaximum?.toString().orEmpty(),
            settings.chartMinimum == null, settings.chartMaximum == null)
    }
}
internal data class NumericSettingsValidation(val settings: WidgetSettings?, val errors: Map<NumericField, String>)
private val decimalPattern = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")

/** Keyboard hints never replace validation. Raw intermediate/pasted input remains editable. */
internal fun validateNumericSettings(type: WidgetType, base: WidgetSettings, draft: NumericSettingsDraft): NumericSettingsValidation {
    val errors = linkedMapOf<NumericField, String>()
    fun number(raw: String, field: NumericField, required: Boolean = false): Double? {
        val text = raw.trim()
        if (text.isEmpty()) { if (required) errors[field] = "Enter a number."; return null }
        val parsed = text.takeIf { it.length <= 256 && decimalPattern.matches(it) }?.toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) { errors[field] = "Enter a finite signed number (for example -1.5 or 2e3)."; return null }
        if (parsed == 0.0 && runCatching { BigDecimal(text).signum() != 0 }.getOrDefault(true)) {
            errors[field] = "Value is smaller than the supported numeric range."; return null
        }
        return parsed
    }
    fun integer(raw: String, field: NumericField, range: IntRange): Int? {
        val text = raw.trim()
        val value = text.takeIf { it.length <= 16 && it.matches(Regex("[0-9]+")) }?.toIntOrNull()
        if (value == null || value !in range) errors[field] = "Enter a whole number from ${range.first} to ${range.last}."
        return value
    }
    val precision = integer(draft.precision, NumericField.PRECISION, 0..6)
    val freshness = integer(draft.freshness, NumericField.FRESHNESS, 1..3600)
    val bounded = type == WidgetType.GAUGE || type == WidgetType.RANGE
    val min = if (bounded) number(draft.minimum, NumericField.MINIMUM, true) else base.minimum
    val max = if (bounded) number(draft.maximum, NumericField.MAXIMUM, true) else base.maximum
    val low = if (bounded) number(draft.lower, NumericField.LOWER) else base.lowerThreshold
    val high = if (bounded) number(draft.upper, NumericField.UPPER) else base.upperThreshold
    if (bounded && min != null && max != null) {
        if (min >= max) { errors[NumericField.MINIMUM] = "Minimum must be less than maximum."; errors[NumericField.MAXIMUM] = "Maximum must be greater than minimum." }
        if (low != null && low !in min..max) errors[NumericField.LOWER] = "Threshold must be within the configured bounds."
        if (high != null && high !in min..max) errors[NumericField.UPPER] = "Threshold must be within the configured bounds."
        if (low != null && high != null && low > high) errors[NumericField.UPPER] = "Upper threshold must not be below lower threshold."
    }
    val chartMin = if (type != WidgetType.CHART) base.chartMinimum else if (draft.autoMinimum) null else number(draft.chartMinimum, NumericField.CHART_MINIMUM, true)
    val chartMax = if (type != WidgetType.CHART) base.chartMaximum else if (draft.autoMaximum) null else number(draft.chartMaximum, NumericField.CHART_MAXIMUM, true)
    if (type == WidgetType.CHART) {
        if (chartMin != null && chartMax != null && chartMin >= chartMax) errors[NumericField.CHART_MAXIMUM] = "Chart maximum must be greater than minimum."
        if (chartMin == Double.MAX_VALUE && chartMax == null) errors[NumericField.CHART_MINIMUM] = "Minimum leaves no finite space above it."
        if (chartMax == -Double.MAX_VALUE && chartMin == null) errors[NumericField.CHART_MAXIMUM] = "Maximum leaves no finite space below it."
    }
    if (draft.window !in listOf(60, 300, 900)) errors[NumericField.GENERAL] = "Choose a supported history window."
    if (errors.isNotEmpty()) return NumericSettingsValidation(null, errors)
    val settings = base.copy(minimum = min, maximum = max, lowerThreshold = low, upperThreshold = high,
        precision = precision!!, staleAfterSeconds = freshness!!, chartWindowSeconds = draft.window, chartMinimum = chartMin, chartMaximum = chartMax)
    settings.validationError()?.let { errors[NumericField.GENERAL] = it }
    return NumericSettingsValidation(settings.takeIf { errors.isEmpty() }, errors)
}
