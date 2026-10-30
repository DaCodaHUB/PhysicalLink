package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacodahub.physicallink.domain.model.DeviceCapability

@Composable
internal fun SensorCard(capability: DeviceCapability, value: String?, controls: WidgetControls? = null, precision: Int = 2, freshness: String? = null, statusIsAlert: Boolean = false) {
    val reading = formatSensorValue(value, capability.type, precision)
    val unit = sensorUnit(capability.unit)
    val waiting = value.isNullOrBlank()
    WidgetContainer((capability.name ?: sensorName(capability.id)), status = freshness ?: if (waiting) "Waiting for a reading" else null, controls = controls, statusIsAlert = statusIsAlert) {
        SensorReading(reading, unit, compact = controls != null, waiting = waiting, numeric = capability.type.equals("number", true))
    }
}

/** A bounded visual number with the full reading retained for assistive technology. */
@Composable
internal fun SensorReading(reading: String, unit: String, compact: Boolean = false, waiting: Boolean = false, numeric: Boolean = true, readingDescription: String? = null) {
    val display = if (numeric) compactNumericReading(reading) else reading
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unitWidth = maxWidth * 0.35f
        val stackedUnit = (unit.length > 3 && maxWidth < 220.dp) || unit.length > 8
        if (stackedUnit) {
            Column(Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = readingDescription ?: if (waiting) "No reading yet" else "$reading $unit"
            }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(display, style = MaterialTheme.typography.headlineLarge,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 16.sp,
                        maxFontSize = if (compact) 24.sp else 36.sp),
                    maxLines = if (numeric) 1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = readingDescription ?: if (waiting) "No reading yet" else listOf(reading, unit).filter { it.isNotEmpty() }.joinToString(" ")
            }) {
            Text(display, Modifier.weight(1f, fill = false).alignByBaseline(), style = MaterialTheme.typography.headlineLarge,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 16.sp,
                    maxFontSize = if (compact) 24.sp else 36.sp),
                maxLines = if (numeric) 1 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (unit.isNotEmpty()) Text(unit, Modifier.widthIn(max = unitWidth).alignByBaseline(),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
}
