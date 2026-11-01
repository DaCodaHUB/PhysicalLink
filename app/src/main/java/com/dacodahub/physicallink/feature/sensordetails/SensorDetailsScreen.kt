package com.dacodahub.physicallink.feature.sensordetails

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dacodahub.physicallink.core.designsystem.*
import com.dacodahub.physicallink.core.designsystem.theme.*
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*

@Composable
internal fun SensorDetailsScreen(target: SensorDetailsTarget, onBack: () -> Unit, viewModel: DashboardViewModel) {
    val input by viewModel.sensorDetails.collectAsStateWithLifecycle()
    val layout by viewModel.detailsLayout.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val widget = layout?.widgets?.firstOrNull { it.id == target.widgetId && it.source == target.source }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        if (viewModel.isDemo) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.simulateDemoWhileVisible() }
    }
    val state = sensorDetailsState(target.source, input, configuredWidget = widget, widgetMissing = layout != null && widget == null)
        .copy(loadingWidget = layout == null)
    SensorDetailsContent(state, onBack, onApply = { settings ->
        viewModel.configureDetailsWidget(target.source, target.widgetId, settings)
    }, persistenceError = dashboard.persistenceError)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SensorDetailsContent(state: SensorDetailsState, onBack: () -> Unit = {},
    onApply: (WidgetSettings) -> Unit = {}, persistenceError: String? = null) {
    val widget = state.reading.widget
    var draft by remember(widget.source, widget.id, widget.settings) { mutableStateOf(NumericSettingsDraft.from(widget.settings)) }
    val validation = validateNumericSettings(widget.type, widget.settings, draft)
    val previewSettings = validation.settings ?: widget.settings
    val preview = state.reading.copy(widget = widget.copy(settings = previewSettings))
    val dirty = draft != NumericSettingsDraft.from(widget.settings)
    BackHandler(onBack = onBack)
    val capability = state.reading.capability
    val name = capability?.name ?: state.source.capabilityId
    val unit = capability?.unit
    val stats = if (widget.type == WidgetType.CHART) preview.timeline?.let { historyStatistics(it.samples, preview.nowMs, previewSettings.chartWindowSeconds) } else null
    val type = state.reading.widget.type
    val title = when (type) { WidgetType.VALUE -> "Value details"; WidgetType.CHART -> "Chart details"; WidgetType.GAUGE -> "Gauge details"; WidgetType.RANGE -> "Range details" }
    CompositionLocalProvider(LocalWidgetAccent provides widgetAccent(state.source.capabilityId)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (state.loadingWidget || state.widgetMissing) "Sensor details" else title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Back to dashboard" }) { ActionGlyph(Glyph.BACK) }
            })
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("sensor-details-list"),
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.widgetMissing || state.loadingWidget) {
                        item { DetailsSection(if (state.loadingWidget) "Loading saved widget" else "Widget no longer available") {
                            Text(if (state.loadingWidget) "Waiting for your saved configuration." else "This widget was removed or the selected device changed. Return to the dashboard.")
                            persistenceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = onBack) { Text("Return to dashboard") }
                        } }
                        return@LazyColumn
                    }
                    persistenceError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
                    item {
                        WidgetContainer(name, status = (if (state.reading.simulated) "Simulated - " else "") + state.status,
                            statusIsAlert = state.status != "Live") {
                            WidgetReading(preview)
                            Text(state.acquisitionAgeMs?.let { "Acquired ${it} ms ago" } ?: "Acquisition age unavailable",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.missing) Text(if (state.status == "Disconnected") "Reconnect this device to view its sensor."
                                else "This sensor is not in the current device metadata.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (type == WidgetType.CHART) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(60, 300, 900).forEach { seconds ->
                                FilterChip(selected = draft.window == seconds, onClick = { draft = draft.copy(window = seconds) },
                                    label = { Text("${seconds / 60} min") }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        Text("Acquisition time / last ${previewSettings.chartWindowSeconds / 60} min / this session",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        if (state.missing) DetailsSection("Reading history") {
                            Text("History unavailable for the current device/session.", style = MaterialTheme.typography.bodyMedium)
                        } else ChartWidget(preview, showReading = false, showStatus = false, title = "Reading history")
                        if (stats?.count == 1) Text("One sample recorded. More readings are needed to show a trend.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    } else if (type == WidgetType.GAUGE || type == WidgetType.RANGE) {
                        item {
                            if (state.missing) DetailsSection("Visualization unavailable") { Text("Reconnect or restore this sensor to see its position.") }
                            else BoundedWidget(preview, showReading = false, showStatus = false,
                                titleOverride = if (type == WidgetType.GAUGE) "Gauge" else "Range position", large = true)
                        }

                    }
                    item {
                        DetailsSection(if (type == WidgetType.VALUE) "Display precision" else "Visualization settings") {
                            NumericSettingsFields(type, true, draft, validation, inlineDetails = true) { draft = it }
                            if (dirty) {
                                Text("Preview only. Apply to save changes.", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { validation.settings?.let(onApply) }, enabled = validation.settings != null,
                                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Apply settings") }
                                    OutlinedButton(onClick = { draft = NumericSettingsDraft.from(widget.settings) },
                                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                                }
                            }
                        }
                    }
                    if (type == WidgetType.CHART) {
                    item {
                        DetailsSection("History statistics") {
                            if (stats == null) Text("No valid retained samples in this window.", style = MaterialTheme.typography.bodyMedium)
                            else {
                                Text("${stats.count} valid samples", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Statistic("Minimum", stats.minimum.toString(), sensorUnit(unit.orEmpty()), previewSettings.precision)
                                    Statistic("Average", stats.average.toString(), sensorUnit(unit.orEmpty()), previewSettings.precision)
                                    Statistic("Maximum", stats.maximum.toString(), sensorUnit(unit.orEmpty()), previewSettings.precision)
                                }
                                val now = state.reading.nowMs
                                Text("Samples acquired ${(now - stats.firstMs) / 1000}s to ${(now - stats.lastMs) / 1000}s ago.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    }
                    item {
                        DetailsSection("Sensor information") {
                            Info("Sensor ID", state.source.capabilityId)
                            Info("Display name", capability?.name ?: if (capability != null) sensorName(capability.id) else "Not in current metadata")
                            Info("Unit", unit?.ifEmpty { "Unitless" } ?: "Unknown")
                            Info("Availability", state.status)
                            Text("Stale uses this widget\u0027s ${state.reading.widget.settings.staleAfterSeconds}-second acquisition-age setting.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.deviceName?.let { Info("Device", it) }
                            Info("Device ID", state.source.deviceId)
                            Text("History is held temporarily in memory and may remain after disconnection or session end. It is lost when the app process ends.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable
private fun Statistic(title: String, raw: String, unit: String, precision: Int) {
    Column(Modifier.widthIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatSensorValue(raw, "number", precision), style = MaterialTheme.typography.titleLarge)
        if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun Info(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
