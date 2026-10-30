package com.dacodahub.physicallink.feature.devices

import com.dacodahub.physicallink.core.designsystem.theme.*
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dacodahub.physicallink.core.designsystem.DeviceConnectionText
import com.dacodahub.physicallink.core.designsystem.ActionGlyph
import com.dacodahub.physicallink.core.designsystem.Glyph
import com.dacodahub.physicallink.core.designsystem.DeviceDetails
import com.dacodahub.physicallink.core.designsystem.connectionHelp
import com.dacodahub.physicallink.core.designsystem.scanHelp
import com.dacodahub.physicallink.core.designsystem.connectionFailureTitle
import com.dacodahub.physicallink.domain.model.*

@Composable
fun DevicesScreen(onOpenDashboard: () -> Unit, onTryDemo: () -> Unit = {}, viewModel: DevicesViewModel = hiltViewModel()) {
    val demoExplored by viewModel.demoExplored.collectAsStateWithLifecycle()
    val showIntroduction by viewModel.showIntroduction.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val scan by viewModel.scanState.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val live by viewModel.liveState.collectAsStateWithLifecycle()
    val metadata by viewModel.manifest.collectAsStateWithLifecycle()
    LaunchedEffect(connection, capabilities, showIntroduction) {
        if (connection == ConnectionState.Connected && capabilities != null && showIntroduction) viewModel.dismissIntroduction()
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var openWhenConnected by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopScan() }
    LaunchedEffect(connection, openWhenConnected, capabilities, metadata) {
        if (connection == ConnectionState.Connected && openWhenConnected) {
            if (capabilities != null) {
                openWhenConnected = false
                onOpenDashboard()
            }
        } else if (connection is ConnectionState.Error || connection == ConnectionState.ConnectionLost) {
            openWhenConnected = false
        }
    }
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var explainPermissions by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionDenied = !permissions.all { results[it] == true || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!permissionDenied) viewModel.startScan()
    }
    val requestScan: () -> Unit = {
        if (permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            permissionDenied = false
            viewModel.startScan()
        } else explainPermissions = true
    }
    if (explainPermissions) PermissionExplanation(Build.VERSION.SDK_INT < 31,
        onDismiss = { explainPermissions = false }, onContinue = {
            explainPermissions = false
            permissionLauncher.launch(permissions)
        })
    var showDiagnostic by rememberSaveable { mutableStateOf(false) }
    if (metadata != null && showDiagnostic) {
        DeviceDiagnosticsScreen(checkNotNull(metadata), live, viewModel::disconnect, { showDiagnostic = false })
        return
    }
    DevicesContent(devices, connection, scan, deviceId, capabilities, permissionDenied,
        onDiagnostic = if (metadata != null) ({ showDiagnostic = true }) else null,
        showIntroduction = showIntroduction, demoExplored = demoExplored, onDismissIntroduction = viewModel::dismissIntroduction,
        onTryDemo = { openWhenConnected = false; viewModel.markDemoExplored(); onTryDemo() },
        onScan = requestScan, onStopScan = viewModel::stopScan,
        onConnect = { address -> openWhenConnected = true; viewModel.connect(address) },
        onCancel = { openWhenConnected = false; viewModel.disconnect() }, onOpenDashboard = onOpenDashboard,
        onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicesContent(
    devices: List<DiscoveredDevice>, connection: ConnectionState, scan: ScanState,
    deviceId: String? = null, capabilities: DeviceCapabilities? = null, permissionDenied: Boolean = false,
    onScan: () -> Unit = {}, onStopScan: () -> Unit = {}, onConnect: (String) -> Unit = {},
    onTryDemo: () -> Unit = {}, onCancel: () -> Unit = {}, onOpenDashboard: () -> Unit = {}, onSettings: () -> Unit = {},
    onDiagnostic: (() -> Unit)? = null,
    showIntroduction: Boolean = false, demoExplored: Boolean = false, onDismissIntroduction: () -> Unit = {}
) {
    val appearanceContext = LocalContext.current
    val appearanceSettings = remember { AppearanceSettings(appearanceContext) }
    val appearance by appearanceSettings.appearance.collectAsStateWithLifecycle(initialValue = Appearance.SYSTEM)
    val appearanceScope = rememberCoroutineScope()
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    if (showAppearance) AlertDialog(onDismissRequest = { showAppearance = false }, title = { Text("Appearance") }, text = {
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = appearance == option, role = Role.RadioButton,
                    onClick = { appearanceScope.launch { appearanceSettings.setAppearance(option) } }),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appearance == option, onClick = null)
                    Text(option.label, Modifier.padding(start = 12.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("Done") } })
    var replayIntroduction by rememberSaveable { mutableStateOf(false) }
    fun dismissIntroduction() { replayIntroduction = false; onDismissIntroduction() }
    var hasScanned by rememberSaveable { mutableStateOf(false) }
    var retrying by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var detailsDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    val selected = devices.find { it.address == deviceId }
    val connected = connection == ConnectionState.Connected
    val connecting = connection == ConnectionState.Connecting || connection == ConnectionState.Discovering
    val name = capabilities?.takeIf { connected }?.device?.takeIf { it.isNotBlank() } ?: selected?.name ?: "PhysicalLink device"
    val canScan = !connected && !connecting
    LaunchedEffect(scan) { if (scan == ScanState.Scanning) hasScanned = true }
    LaunchedEffect(connection) { if (!connecting) retrying = false }

    var explainDiscovery by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = explainDiscovery) { explainDiscovery = false }
    if (explainDiscovery) AlertDialog(onDismissRequest = { explainDiscovery = false },
        title = { Text("Find another device") },
        text = { Text("PhysicalLink connects to one device at a time. Disconnect this device, then tap Scan for devices. Your dashboard layout is saved.") },
        confirmButton = { TextButton(onClick = { explainDiscovery = false; onCancel() }) { Text("Disconnect device") } },
        dismissButton = { TextButton(onClick = { explainDiscovery = false }) { Text("Keep connected") } })

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("PhysicalLink", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Device overview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, actions = {

            IconButton(onClick = { showAppearance = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Appearance" }) { ActionGlyph(Glyph.SETTINGS) }
            TextButton(onClick = { showGuide = true }, modifier = Modifier.semantics { contentDescription = "Getting Started" }) { Text("Help") }
            IconButton(onClick = { detailsDevice = selected; showDetails = true },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Device details" }) { ActionGlyph(Glyph.INFO) }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.testTag("device-overview").widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Your connections", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                }
                if (connecting) item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        DeviceConnectionText(connection)
                    }
                }
                when {
                    connecting -> item {
                        StatePanel(if (retrying) "Retrying connection" else "Connecting to your device", "Retrieving sensors and synchronizing readings. Keep the device nearby.", progress = true) {
                            OutlinedButton(onClick = onCancel) { Text("Cancel connection") }
                        }
                    }
                    connected -> item {
                        ConnectedDeviceCard(name, selected?.rssi, capabilities?.capabilities?.size,
                            onOpenDashboard, onDiagnostic, onCancel)
                    }
                }
                if (!connected && !connecting && connection !is ConnectionState.Error && connection != ConnectionState.ConnectionLost) item {
                    if (showIntroduction || replayIntroduction) StatePanel("Welcome to PhysicalLink", "Connect a project to create a dashboard from its sensors.") {
                        Button(onClick = { dismissIntroduction(); onScan() }) { Text("Connect a Device") }
                        TextButton(onClick = ::dismissIntroduction) { Text("Not now") }
                    } else StatePanel("No device connected", "Your sensor projects live here. Find a device below or explore Demo mode.")
                }
                if (canScan) {
                    if (!permissionDenied && scan !is ScanState.Error && (connection is ConnectionState.Error || connection == ConnectionState.ConnectionLost)) item {
                        StatePanel(if (connection == ConnectionState.ConnectionLost) "Device disconnected" else connectionFailureTitle(connection),
                            connectionHelp(connection), error = true,
                            permissionWarning = (connection as? ConnectionState.Error)?.message?.contains("permission", true) == true) {
                            if ((connection as? ConnectionState.Error)?.message?.contains("permission", true) == true)
                                TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
                            else if (!deviceId.isNullOrBlank()) OutlinedButton(onClick = {
                                retrying = true
                                onConnect(deviceId)
                            }) { Text("Retry connection") }
                        }
                    }
                    if (permissionDenied || scan is ScanState.Error) item {
                        StatePanel("Scanning needs attention", if (permissionDenied) "Allow Nearby devices permission in app settings, then scan again. On Android 11 or earlier, allow Location."
                            else scanHelp((scan as ScanState.Error).message), error = true,
                            permissionWarning = permissionDenied || (scan as? ScanState.Error)?.message?.contains("permission", true) == true) {
                            if (permissionDenied || (scan as? ScanState.Error)?.message?.contains("permission", true) == true)
                                TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
                        }
                    }
                }
                item { Text("Explore", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
                item {
                    ExploreDiscovery(connected, connecting, scan == ScanState.Scanning,
                        onFind = { if (connected) explainDiscovery = true else onScan() }, onStop = onStopScan)
                }

                if (canScan) {
                    if (hasScanned && devices.isEmpty() && scan == ScanState.Idle && !permissionDenied && connection !is ConnectionState.Error && connection != ConnectionState.ConnectionLost) item {
                        StatePanel(if (hasScanned) "No compatible devices found" else "No devices found yet", "Power on your device running PhysicalLink firmware. Keep it nearby and tap Scan. See Help if it does not appear.")
                    }
                    if (devices.isNotEmpty()) {
                        item {
                            Text("Nearby devices", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                            if (scan != ScanState.Scanning) Text("Seen in the last scan. Availability may change.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(devices, key = { it.address }) { device ->
                            DeviceCard(device, onConnect = { onConnect(device.address) },
                                onDetails = { detailsDevice = device; showDetails = true })
                        }
                    }
                }
                if (!canScan && devices.any { it.address != deviceId }) {
                    item { Text("Other devices", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
                    item { Text("Seen in the last scan. Disconnect before connecting another device.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(devices.filter { it.address != deviceId }, key = { "other:${it.address}" }) { device ->
                        DeviceCard(device, onConnect = {}, onDetails = { detailsDevice = device; showDetails = true }, enabled = false)
                    }
                }
                item { ExploreDemo { dismissIntroduction(); onTryDemo() } }
            }
        }
    }
    if (showGuide) GettingStarted(onDismiss = { showGuide = false }, onReplayIntroduction = {
        showGuide = false
        replayIntroduction = true
    })
    if (showDetails) {
        val address = detailsDevice?.address ?: deviceId
        val current = address == deviceId
        DeviceDetails(detailsDevice?.name ?: name, address, detailsDevice?.rssi,
            capabilities.takeIf { current }, if (current) connection else ConnectionState.Disconnected, scan,
            onDismiss = { showDetails = false },
            onDisconnect = if (current && (connected || connecting)) ({ showDetails = false; onCancel() }) else null)
    }
}

@Composable
private fun StatePanel(
    title: String, message: String, progress: Boolean = false, error: Boolean = false, featured: Boolean = false,
    permissionWarning: Boolean = false, actions: @Composable ColumnScope.() -> Unit = {}
) {
    Surface(shape = MaterialTheme.shapes.large,
        color = if (permissionWarning) MaterialTheme.colorScheme.surfaceContainerLow else if (error) MaterialTheme.colorScheme.errorContainer else if (featured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (permissionWarning) MaterialTheme.colorScheme.onSurface else if (error) MaterialTheme.colorScheme.onErrorContainer else if (featured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (progress) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (permissionWarning) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionGlyph(Glyph.INFO, Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            } else Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}
