package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.ActionGlyph
import com.dacodahub.physicallink.core.designsystem.Glyph
import com.dacodahub.physicallink.domain.model.WidgetType

// Extend only after the matching real renderer exists; no nonfunctional choices are offered.
internal val supportedWidgetTypes = listOf(WidgetType.VALUE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetConfigurationSheet(
    state: DashboardWidgetState, deviceName: String, index: Int, count: Int,
    actions: DashboardActions, onDismiss: () -> Unit
) {
    val widget = state.widget
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Widget settings", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(sensorName(widget.source.capabilityId), style = MaterialTheme.typography.titleMedium)
            Text("Source: $deviceName\nSensor: ${widget.source.capabilityId}\nDevice: ${widget.source.deviceId}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.capability == null) Text("Sensor unavailable. Your configuration is retained.", style = MaterialTheme.typography.bodySmall)
            Text("Widget width", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = widget.columnSpan == 1, onClick = { actions.resize(widget.id, 1) },
                    label = { Text("Half") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = widget.columnSpan == 2, onClick = { actions.resize(widget.id, 2) },
                    label = { Text("Full") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text("Half uses one column when space allows.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Visualization", style = MaterialTheme.typography.titleSmall)
            if (widget.type !in supportedWidgetTypes) Text("Saved visualization: ${widget.type.name.lowercase()}. Not available yet.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                supportedWidgetTypes.forEach { type ->
                    FilterChip(selected = widget.type == type, onClick = { actions.useValue(widget.id) },
                        label = { Text("Value") }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.moveBy(widget.id, -1) }, enabled = index > 0) { Text("Move up") }
                OutlinedButton(onClick = { actions.moveBy(widget.id, 1) }, enabled = index < count - 1) { Text("Move down") }
            }
            TextButton(onClick = { actions.add(widget.source); onDismiss() }, enabled = state.capability != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Duplicate widget") }
            TextButton(onClick = { actions.hide(widget.id); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Hide widget") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close settings") }
        }
    }
}
