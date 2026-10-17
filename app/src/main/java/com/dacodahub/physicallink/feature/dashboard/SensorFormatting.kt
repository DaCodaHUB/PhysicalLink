package com.dacodahub.physicallink.feature.dashboard

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

internal fun formatSensorValue(value: String?, type: String): String {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return "--"
    if (!type.equals("number", ignoreCase = true)) return text
    val number = text.toBigDecimalOrNull() ?: return text
    val rounded = number.setScale(2, RoundingMode.HALF_UP)
    return if (rounded.stripTrailingZeros().scale() <= 0) {
        rounded.setScale(0).toPlainString()
    } else {
        rounded.toPlainString()
    }
}
