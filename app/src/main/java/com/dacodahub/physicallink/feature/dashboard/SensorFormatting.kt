package com.dacodahub.physicallink.feature.dashboard

import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

internal fun sensorName(id: String): String = id
    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    .replace(Regex("[_\\-\\s]+"), " ")
    .trim()
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    .ifEmpty { "Sensor" }

internal fun sensorUnit(unit: String): String = when (unit.trim().lowercase(Locale.ROOT)) {
    "raw" -> ""
    "c", "\u00B0c", "celsius" -> "\u00B0C"
    else -> unit.trim()
}

internal fun formatSensorValue(value: String?, type: String, precision: Int = 2): String {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "--"
    if (!type.equals("number", ignoreCase = true)) return text
    val number = text.toBigDecimalOrNull() ?: return text
    // Bound BigDecimal expansion before rounding or toPlainString can allocate enormous output.
    if (number.precision() > 64 || number.scale().toLong() !in -64L..64L) return text
    val rounded = try { number.setScale(precision.coerceIn(0, 6), RoundingMode.HALF_UP) }
        catch (_: ArithmeticException) { return text }
    // Keep small nonzero measurements meaningful without changing stored values or history.
    if (rounded.signum() == 0 && number.signum() != 0) {
        return number.round(MathContext(3, RoundingMode.HALF_UP)).stripTrailingZeros().toString()
    }
    return if (rounded.stripTrailingZeros().scale() <= 0) {
        rounded.setScale(0).toPlainString()
    } else {
        rounded.toPlainString()
    }
}

/** Avoid a long string of digits dominating a narrow card. Never expand extreme exponents. */
internal fun compactNumericReading(formatted: String): String {
    if (formatted.length <= 12) return formatted
    val number = formatted.toBigDecimalOrNull() ?: return formatted
    return runCatching { number.round(java.math.MathContext(6, java.math.RoundingMode.HALF_UP)).stripTrailingZeros().toString() }.getOrDefault(formatted)
}
