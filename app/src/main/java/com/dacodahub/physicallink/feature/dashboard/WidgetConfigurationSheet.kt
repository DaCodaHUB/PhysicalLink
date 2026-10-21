package com.dacodahub.physicallink.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.ActionGlyph
import com.dacodahub.physicallink.core.designsystem.Glyph
import com.dacodahub.physicallink.domain.model.*

// Numeric choices correspond to real renderers; text sensors retain Value.
internal val supportedWidgetTypes = WidgetType.entries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetConfigurationSheet(
    state: DashboardWidgetState, deviceName: String, index: Int, count: Int,
    actions: DashboardActions, onDismiss: () -> Unit
) {
    val widget = state.widget
    var type by remember(widget.id, widget.type) { mutableStateOf(widget.type) }
    var minimum by remember(widget.id, widget.settings) { mutableStateOf(widget.settings.minimum?.toString().orEmpty()) }
    var maximum by remember(widget.id, widget.settings) { mutableStateOf(widget.settings.maximum?.toString().orEmpty()) }
    var low by remember(widget.id, widget.settings) { mutableStateOf(widget.settings.lowerThreshold?.toString().orEmpty()) }
    var high by remember(widget.id, widget.settings) { mutableStateOf(widget.settings.upperThreshold?.toString().orEmpty()) }
    var precision by remember(widget.id, widget.settings) { mutableStateOf(widget.settings.precision.toString()) }
    var freshness by remember(widget.id, widget.settings) { mutableStateOf(widget.settings.staleAfterSeconds.toString()) }
    var window by remember(widget.id, widget.settings) { mutableIntStateOf(widget.settings.chartWindowSeconds) }
    var error by remember(widget.id) { mutableStateOf<String?>(null) }
    var widthSelected by remember(widget.id) { mutableStateOf(false) }
    val defaultChartWidth = type == WidgetType.CHART && widget.type != WidgetType.CHART && !widthSelected
    val displayedWidth = if (defaultChartWidth) 2 else widget.columnSpan
    val numeric = state.capability?.type?.equals("number", true) == true
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
                FilterChip(selected = displayedWidth == 1, onClick = { widthSelected = true; actions.resize(widget.id, 1) },
                    label = { Text("Half") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = displayedWidth == 2, onClick = { widthSelected = true; actions.resize(widget.id, 2) },
                    label = { Text("Full") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text("Half uses one column when space allows.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Visualization", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                supportedWidgetTypes.filter { numeric || it == WidgetType.VALUE }.forEach { choice ->
                    FilterChip(selected = type == choice, onClick = { type = choice; error = null },
                        label = { Text(choice.name.lowercase().replaceFirstChar { it.titlecase() }) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (type == WidgetType.CHART) {
                Text("Chart time window")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 300, 900).forEach { seconds -> FilterChip(selected = window == seconds,
                        onClick = { window = seconds }, label = { Text("${seconds / 60} min") }) }
                }
            }
            if (type == WidgetType.GAUGE || type == WidgetType.RANGE) {
                OutlinedTextField(minimum, { minimum = it }, label = { Text("Minimum") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(maximum, { maximum = it }, label = { Text("Maximum") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(low, { low = it }, label = { Text("Lower threshold (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(high, { high = it }, label = { Text("Upper threshold (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (numeric) OutlinedTextField(precision, { precision = it }, label = { Text("Decimal places (0-6)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(freshness, { freshness = it }, label = { Text("Stale after (seconds)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Button(onClick = {
                val bounded = type == WidgetType.GAUGE || type == WidgetType.RANGE
                val fields = if (bounded) listOf(minimum, maximum, low, high) else listOf(
                    widget.settings.minimum, widget.settings.maximum, widget.settings.lowerThreshold, widget.settings.upperThreshold
                ).map { it?.toString().orEmpty() }
                val parsed = fields.map { it.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull() }
                val settings = WidgetSettings(window, parsed[0], parsed[1], parsed[2], parsed[3], precision.toIntOrNull() ?: -1, freshness.toIntOrNull() ?: -1)
                error = if (fields.zip(parsed).any { (text, number) -> text.isNotBlank() && number == null }) "Enter valid numeric bounds and thresholds."
                    else settings.validationError() ?: if ((type == WidgetType.GAUGE || type == WidgetType.RANGE) && !settings.hasRange) "Set both minimum and maximum." else null
                if (error == null) {
                    actions.configure(widget.id, type, settings)
                    // Existing charts and explicitly chosen widths are never resized on Apply.
                    if (defaultChartWidth && widget.columnSpan != 2) actions.resize(widget.id, 2)
                    onDismiss()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Apply settings") }
            HorizontalDivider()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.moveBy(widget.id, -1) }, enabled = index > 0) { Text("Move up") }
                OutlinedButton(onClick = { actions.moveBy(widget.id, 1) }, enabled = index < count - 1) { Text("Move down") }
            }
            TextButton(onClick = { actions.duplicate(widget.id); onDismiss() }, enabled = state.capability != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Duplicate widget") }
            TextButton(onClick = { actions.hide(widget.id); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Hide widget") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close settings") }
        }
    }
}
