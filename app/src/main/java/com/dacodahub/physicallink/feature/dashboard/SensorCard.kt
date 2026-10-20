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
internal fun SensorCard(capability: DeviceCapability, value: String?, controls: WidgetControls? = null, precision: Int = 2, freshness: String? = null) {
    val reading = formatSensorValue(value, capability.type, precision)
    val unit = sensorUnit(capability.unit)
    val waiting = value.isNullOrBlank()
    WidgetContainer(sensorName(capability.id), status = freshness ?: if (waiting) "Waiting for a reading" else null, controls = controls) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = if (waiting) "No reading yet" else listOf(reading, unit).filter { it.isNotEmpty() }.joinToString(" ")
            }) {
            Text(reading, style = MaterialTheme.typography.headlineLarge,
                fontSize = when { reading.length > 18 -> 22.sp; reading.length > 10 -> 28.sp; else -> 36.sp },
                softWrap = true)
            if (unit.isNotEmpty()) Text(unit, Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
