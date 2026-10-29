package com.dacodahub.physicallink.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dacodahub.physicallink.core.designsystem.*
import com.dacodahub.physicallink.domain.model.*

/** UI events only; saved state still belongs to the existing ViewModel/repository. */
internal data class DashboardActions(
    val devices: () -> Unit = {}, val disconnect: () -> Unit = {}, val editing: (Boolean) -> Unit = {},
    val move: (String, String) -> Unit = { _, _ -> }, val moveBy: (String, Int) -> Unit = { _, _ -> },
    val hide: (String) -> Unit = {}, val restore: (String) -> Unit = {},
    val duplicate: (String) -> Unit = {},
    val add: (SensorSource) -> Unit = {}, val reset: () -> Unit = {},
    val configure: (String, WidgetType, WidgetSettings) -> Unit = { _, _, _ -> },
    val resize: (String, Int) -> Unit = { _, _ -> }, val useValue: (String) -> Unit = {}
)

@Composable
fun DashboardScreen(onDevices: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        if (viewModel.isDemo) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.simulateDemoWhileVisible() }
    }
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    LaunchedEffect(connection) {
        if (connection == ConnectionState.Disconnected || connection == ConnectionState.ConnectionLost || connection is ConnectionState.Error) onDevices()
    }
    DashboardContent(connection, dashboard, deviceId, capabilities, devices.find { it.address == deviceId }?.rssi,
        DashboardActions(devices = onDevices, disconnect = viewModel::disconnect, editing = viewModel::setEditing,
            move = viewModel::moveWidget, moveBy = viewModel::moveWidgetBy, hide = viewModel::hideWidget,
            duplicate = viewModel::duplicateWidget, restore = viewModel::restoreWidget, add = viewModel::addWidget, reset = viewModel::resetLayout,
            configure = viewModel::configureWidget, resize = viewModel::resizeWidget, useValue = viewModel::useValueWidget), isDemo = viewModel.isDemo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardContent(
    connection: ConnectionState, dashboard: DashboardState, deviceId: String? = null,
    capabilities: DeviceCapabilities? = null, rssi: Int? = null, actions: DashboardActions = DashboardActions(),
    isDemo: Boolean = false
) {
    val grid = rememberLazyGridState()
    var showAdd by remember(deviceId) { mutableStateOf(false) }
    var showReset by remember(deviceId) { mutableStateOf(false) }
    var showDetails by remember(deviceId) { mutableStateOf(false) }
    var menu by remember(deviceId) { mutableStateOf(false) }
    var configuringId by remember(deviceId) { mutableStateOf<String?>(null) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var movement by remember { mutableStateOf(Offset.Zero) }
    var targetId by remember { mutableStateOf<String?>(null) }
    val visibleIds = dashboard.widgets.map { it.widget.id }.toSet()
    val currentIds by rememberUpdatedState(visibleIds)
    val currentActions by rememberUpdatedState(actions)
    fun clearDrag() { draggingId = null; targetId = null; movement = Offset.Zero }
    val connected = connection == ConnectionState.Connected
    val name = dashboard.deviceName?.takeIf { it.isNotBlank() } ?: capabilities?.device?.takeIf { it.isNotBlank() } ?: "Sensor project"
    LaunchedEffect(dashboard.isEditing, visibleIds, connection) {
        if (!dashboard.isEditing || draggingId !in visibleIds || !connected) clearDrag()
        if (!dashboard.isEditing || !connected) { showAdd = false; showReset = false; configuringId = null }
        if (configuringId !in visibleIds) configuringId = null
        if (!connected) currentActions.editing(false)
    }
    LaunchedEffect(deviceId) { clearDrag() }
    BackHandler(enabled = isDemo && !dashboard.isEditing) { actions.devices() }
    BackHandler(enabled = dashboard.isEditing) { clearDrag(); actions.editing(false) }

    Scaffold(topBar = {
        TopAppBar(title = {
            Column(Modifier.testTag("device-header")) {
                Text(if (dashboard.isEditing) "Edit dashboard" else name, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (dashboard.isEditing) Text(if (isDemo) "$name - Demo Mode" else name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (isDemo) Text("Demo Mode - Simulated readings", style = MaterialTheme.typography.bodySmall)
                else DeviceConnectionText(connection)
            }
        }, navigationIcon = {
            if (!dashboard.isEditing) IconButton(onClick = actions.devices,
                modifier = Modifier.semantics { contentDescription = "Devices" }) { ActionGlyph(Glyph.BACK) }
        }, actions = {
            if (isDemo) TextButton(onClick = actions.devices) { Text("Exit Demo") }
            if (dashboard.isEditing) {
                TextButton(onClick = { clearDrag(); actions.editing(false) }) { Text("Done") }
            } else {
                if (connected) IconButton(onClick = { actions.editing(true) }, enabled = !dashboard.loadingLayout && !dashboard.discovering,
                    modifier = Modifier.semantics { contentDescription = "Edit dashboard" }) { ActionGlyph(Glyph.EDIT) }
                if (!isDemo) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "More device actions" }) {
                        ActionGlyph(Glyph.MORE)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Device details") }, onClick = { menu = false; showDetails = true })
                        if (connected || connection == ConnectionState.Connecting || connection == ConnectionState.Discovering)
                            DropdownMenuItem(text = { Text(if (connected) "Disconnect" else "Cancel connection") },
                                onClick = { menu = false; actions.disconnect() })
                    }
                }
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                val minimumWidth = 156.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                val columns = if (maxWidth - 44.dp >= minimumWidth * 2) 2 else 1
                LazyVerticalGrid(columns = GridCells.Fixed(columns), state = grid,
                    modifier = Modifier.testTag("dashboard-grid").onGloballyPositioned { gridOrigin = it.positionInRoot() },
                    userScrollEnabled = draggingId == null, contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isDemo) item(key = "demo-explanation", span = { GridItemSpan(maxLineSpan) }) {
                        Text("These readings are simulated. Exit Demo to connect your own ESP32.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!connected || dashboard.loadingLayout || dashboard.discovering || dashboard.persistenceError != null || dashboard.widgets.isEmpty()) {
                        item(key = "dashboard-notice", span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                dashboard.persistenceError?.let {
                                    Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                                }
                                when {
                                    !connected -> {
                                        DashboardNotice(if (connection == ConnectionState.Connecting || connection == ConnectionState.Discovering)
                                            "Connecting to your project" else "Return to Devices to reconnect.",
                                            progress = connection == ConnectionState.Connecting || connection == ConnectionState.Discovering)
                                    }
                                    dashboard.loadingLayout -> DashboardNotice("Loading your saved dashboard", progress = true)
                                    dashboard.discovering -> DashboardNotice("Finding your project's sensors", progress = true)
                                    dashboard.widgets.isEmpty() -> DashboardNotice(if (dashboard.hiddenWidgets.isNotEmpty())
                                        "Your widgets are hidden. Edit dashboard to restore them." else "No sensors available. Check Device details.")
                                }
                            }
                        }
                    }
                    if (connected) {
                        itemsIndexed(dashboard.widgets, key = { _, state -> state.widget.id },
                            span = { _, state -> GridItemSpan(state.widget.columnSpan.coerceIn(1, columns)) }) { index, state ->
                            val id = state.widget.id
                            val title = (state.capability?.name ?: sensorName(state.widget.source.capabilityId))
                            val dragging = draggingId == id
                            val target = targetId == id
                            var handleOrigin by remember(id) { mutableStateOf(Offset.Zero) }
                            val dragModifier = Modifier.onGloballyPositioned { handleOrigin = it.positionInRoot() }
                                .pointerInput(id, deviceId) {
                                    detectDragGesturesAfterLongPress(onDragStart = { position ->
                                        draggingId = id; dragStart = handleOrigin + position - gridOrigin
                                        movement = Offset.Zero; targetId = null
                                    }, onDragCancel = { clearDrag() }, onDragEnd = {
                                        if (id in currentIds) targetId?.takeIf { it in currentIds }?.let { currentActions.move(id, it) }
                                        clearDrag()
                                    }, onDrag = { change, amount ->
                                        change.consume(); movement += amount
                                        val point = dragStart + movement
                                        targetId = grid.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                            item.key != id && item.key in currentIds && point.x >= item.offset.x &&
                                                point.x < item.offset.x + item.size.width && point.y >= item.offset.y &&
                                                point.y < item.offset.y + item.size.height
                                        }?.key as? String
                                    })
                                }
                            Box(Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f).graphicsLayer {
                                translationX = if (dragging) movement.x else 0f
                                translationY = if (dragging) movement.y else 0f
                                alpha = if (dragging) 0.85f else 1f
                            }.then(if (dragging || target) Modifier.border(3.dp, MaterialTheme.colorScheme.primary,
                                MaterialTheme.shapes.large) else Modifier).semantics {
                                if (dragging) stateDescription = "Moving $title"
                                else if (target) stateDescription = "Drop target"
                                if (dashboard.isEditing && draggingId == null) customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction("Move up") { actions.moveBy(id, -1); true })
                                    if (index < dashboard.widgets.lastIndex) add(CustomAccessibilityAction("Move down") { actions.moveBy(id, 1); true })
                                }
                            }) {
                                DashboardWidgetCard(state, if (dashboard.isEditing) WidgetControls(index, dragModifier,
                                    draggingId == null, configure = { configuringId = id }, target = target) else null)
                            }
                        }
                        if (dashboard.isEditing) item(key = "dashboard-edit-footer", span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { showAdd = true }, enabled = !dashboard.loadingLayout,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    ActionGlyph(Glyph.ADD); Spacer(Modifier.width(8.dp)); Text("Add widget")
                                }
                                if (dashboard.hiddenWidgets.isNotEmpty()) Text("${dashboard.hiddenWidgets.size} hidden widgets available to restore",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { showReset = true }, enabled = !dashboard.loadingLayout,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Reset layout") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd && dashboard.isEditing) AddWidgetSheet(dashboard, actions) { showAdd = false }
    if (showReset && dashboard.isEditing) AlertDialog(onDismissRequest = { showReset = false }, title = { Text("Reset dashboard?") },
        text = { Text("Return to one Value widget per available sensor. This removes your arrangement, hidden widgets, and extra copies for this device.") },
        confirmButton = { TextButton(onClick = { clearDrag(); actions.reset(); showReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("Keep layout") } })
    val configuring = dashboard.widgets.find { it.widget.id == configuringId }
    if (configuring != null && dashboard.isEditing && connected) key(deviceId, configuring.widget.id) {
        WidgetConfigurationSheet(configuring, name, dashboard.widgets.indexOf(configuring), dashboard.widgets.size,
            actions, onDismiss = { configuringId = null })
    }
    if (showDetails) DeviceDetails(name, deviceId, rssi, capabilities, connection,
        onDismiss = { showDetails = false }, onDisconnect = if (connected) ({ showDetails = false; actions.disconnect() }) else null)
}

@Composable
private fun DashboardNotice(message: String, progress: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (progress) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
