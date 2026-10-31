package com.dacodahub.physicallink.feature.sensordetails

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var showSettings by rememberSaveable(target.source.deviceId, target.source.capabilityId, target.widgetId) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        if (viewModel.isDemo) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.simulateDemoWhileVisible() }
    }
    val state = sensorDetailsState(target.source, input, configuredWidget = widget, widgetMissing = layout != null && widget == null)
        .copy(loadingWidget = layout == null)
    LaunchedEffect(state.widgetMissing) { if (state.widgetMissing) showSettings = false }
    SensorDetailsContent(state, onBack,
        onWindow = { seconds -> widget?.let { viewModel.configureDetailsWidget(target.source, target.widgetId, it.settings.copy(chartWindowSeconds = seconds)) } },
        onSettings = { showSettings = true }, persistenceError = dashboard.persistenceError)
    if (showSettings && widget != null) VisualizationSettingsSheet(widget, onSave = {
        viewModel.configureDetailsWidget(target.source, target.widgetId, it)
        showSettings = false
    }, onDismiss = { showSettings = false })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SensorDetailsContent(state: SensorDetailsState, onBack: () -> Unit = {}, onWindow: (Int) -> Unit = {},
    onSettings: () -> Unit = {}, persistenceError: String? = null) {
    BackHandler(onBack = onBack)
    val capability = state.reading.capability
    val name = capability?.name ?: state.source.capabilityId
    val unit = capability?.unit
    val stats = state.statistics
    val type = state.reading.widget.type
    val title = when (type) { WidgetType.VALUE -> "Value details"; WidgetType.CHART -> "Chart details"; WidgetType.GAUGE -> "Gauge details"; WidgetType.RANGE -> "Range details" }
    CompositionLocalProvider(LocalWidgetAccent provides widgetAccent(state.source.capabilityId)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (state.loadingWidget || state.widgetMissing) "Sensor details" else title, style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Back to dashboard" }) { ActionGlyph(Glyph.BACK) }
            }, actions = {
                IconButton(onClick = onSettings, enabled = !state.widgetMissing && !state.loadingWidget,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Visualization settings" }) { ActionGlyph(Glyph.SETTINGS) }
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
                            WidgetReading(state.reading)
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
                                FilterChip(selected = state.windowSeconds == seconds, onClick = { onWindow(seconds) },
                                    label = { Text("${seconds / 60} min") }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        Text("Acquisition time / last ${state.windowSeconds / 60} min / this session",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        if (state.missing) DetailsSection("Reading history") {
                            Text("History unavailable for the current device/session.", style = MaterialTheme.typography.bodyMedium)
                        } else ChartWidget(state.reading, showReading = false, showStatus = false, title = "Reading history")
                        if (stats?.count == 1) Text("One sample recorded. More readings are needed to show a trend.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        DetailsSection("History statistics") {
                            if (stats == null) Text("No valid retained samples in this window.", style = MaterialTheme.typography.bodyMedium)
                            else {
                                Text("${stats.count} valid samples", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Statistic("Minimum", stats.minimum.toString(), sensorUnit(unit.orEmpty()), state.reading.widget.settings.precision)
                                    Statistic("Average", stats.average.toString(), sensorUnit(unit.orEmpty()), state.reading.widget.settings.precision)
                                    Statistic("Maximum", stats.maximum.toString(), sensorUnit(unit.orEmpty()), state.reading.widget.settings.precision)
                                }
                                val now = state.reading.nowMs
                                Text("Samples acquired ${(now - stats.firstMs) / 1000}s to ${(now - stats.lastMs) / 1000}s ago.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    } else if (type == WidgetType.GAUGE || type == WidgetType.RANGE) {
                        item {
                            if (state.missing) DetailsSection("Visualization unavailable") { Text("Reconnect or restore this sensor to see its position.") }
                            else BoundedWidget(state.reading, showReading = false, showStatus = false,
                                titleOverride = if (type == WidgetType.GAUGE) "Gauge" else "Range position", large = true)
                        }
                        item { TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit bounds and precision") } }
                    } else {
                        item { DetailsSection("Display precision") {
                            Text("${state.reading.widget.settings.precision} decimal places", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit precision") }
                        } }
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
                            Text("History is bounded to this session and is not saved permanently.",
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
