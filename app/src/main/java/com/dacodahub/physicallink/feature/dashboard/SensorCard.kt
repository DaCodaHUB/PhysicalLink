package com.dacodahub.physicallink.feature.dashboard
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.domain.model.DeviceCapability

@Composable
internal fun SensorCard(capability: DeviceCapability, value: String?) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(sensorName(capability.id), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatSensorValue(value, capability.type), style = MaterialTheme.typography.headlineLarge)
            val unit = sensorUnit(capability.unit)
            if (unit.isNotEmpty()) {
                Text(unit, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
