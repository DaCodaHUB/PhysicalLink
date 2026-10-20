package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.feature.devices.DevicesContent
import com.dacodahub.physicallink.feature.devices.signalStrengthLabel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DashboardUiTest {
    @get:Rule val compose = createComposeRule()
    private val capabilities = DeviceCapabilities(1, "Test project", listOf(DeviceCapability("temperature", "5679", "C", "number")))
    private val initial = dashboardState("board", capabilities, emptyMap(), null)
    private fun configure() = compose.onNodeWithContentDescription("Configure Temperature widget 1").performScrollTo().performClick()
    private fun footer(text: String) {
        compose.onNodeWithTag("dashboard-grid").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    @Test fun `compact header enters edit mode and sheet duplicates the same source`() {
        var editing by mutableStateOf(false)
        var duplicated: String? = null
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, initial.copy(isEditing = editing), "board", capabilities,
                actions = DashboardActions(editing = { editing = it }, duplicate = { duplicated = it }))
        } }
        compose.onNodeWithText("Test project").assertIsDisplayed()
        compose.onNodeWithText("Connected").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configure Temperature widget 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit dashboard").performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
        configure()
        compose.onNodeWithText("Widget settings").assertIsDisplayed()
        compose.onNodeWithText("Move up").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Duplicate widget").performScrollTo().performClick()
        assertEquals(initial.widgets.single().widget.id, duplicated)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Configure Temperature widget 1").assertDoesNotExist()
    }

    @Test fun `technical metadata and disconnect live in overflow and details`() {
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, initial, "board", capabilities)
        } }
        compose.onNodeWithText("UUID: 5679", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Disconnect").assertDoesNotExist()
        compose.onNodeWithContentDescription("More device actions").performClick()
        compose.onNodeWithText("Disconnect").assertExists()
        compose.onNodeWithText("Device details").performClick()
        compose.onNodeWithText("Bluetooth address").assertExists()
        compose.onNodeWithText("UUID: 5679", substring = true).assertExists()
    }

    @Test fun `reset at grid bottom requires confirmation and can be cancelled`() {
        var resets = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, initial.copy(isEditing = true), "board", capabilities,
                actions = DashboardActions(reset = { resets++ }))
        } }
        footer("Reset layout")
        assertEquals(0, resets)
        compose.onNodeWithText("Keep layout").performClick()
        assertEquals(0, resets)
        footer("Reset layout")
        compose.onNodeWithText("Reset", useUnmergedTree = true).performClick()
        assertEquals(1, resets)
    }

    @Test fun `single scan action follows idle scanning disabled and permission error states`() {
        var scan by mutableStateOf<ScanState>(ScanState.Idle)
        var denied by mutableStateOf(false)
        var started = 0
        var stopped = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, scan, permissionDenied = denied,
                onScan = { started++ }, onStopScan = { stopped++ })
        } }
        assertEquals(0, started)
        compose.onNodeWithText("No devices found yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Scan for devices").performClick()
        assertEquals(1, started)
        compose.runOnIdle { scan = ScanState.Scanning }
        compose.onNodeWithContentDescription("Scan for devices").assertDoesNotExist()
        compose.onNodeWithText("No devices found yet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Stop scanning").performClick()
        assertEquals(1, stopped)
        compose.runOnIdle { scan = ScanState.Error("Bluetooth is disabled. Turn it on, then scan again.") }
        compose.onNodeWithText("Turn on Bluetooth, then scan again.").assertIsDisplayed()
        compose.runOnIdle { scan = ScanState.Idle; denied = true }
        compose.onNodeWithText("Open app settings").assertIsDisplayed()
        compose.onNodeWithText("Scanning needs attention").assertIsDisplayed()
    }

    @Test fun `raw scan errors appear only in details`() {
        val message = "Scan failed (code 2). Retry."
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Error(message))
        } }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithContentDescription("Device details").performClick()
        compose.onNodeWithText(message).assertExists()
    }

    @Test fun `device row is fully tappable but opening details never connects`() {
        val device = DiscoveredDevice("Maker project", "AA:BB:CC:DD:EE:FF", -55)
        var selected: String? = null
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(listOf(device), ConnectionState.Disconnected, ScanState.Idle, onConnect = { selected = it })
        } }
        assertNull(selected)
        compose.onNodeWithText("Strong signal").assertExists()
        compose.onNodeWithText("dBm", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Details for Maker project").performClick()
        assertNull(selected)
        compose.onNodeWithText("-55 dBm", substring = true).assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("device:${device.address}").performClick()
        assertEquals(device.address, selected)
    }

    @Test fun `connecting can be cancelled and connected Devices only offers dashboard`() {
        var state by mutableStateOf<ConnectionState>(ConnectionState.Connecting)
        var cancelled = false
        var opened = false
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), state, ScanState.Idle, "board", capabilities,
                onCancel = { cancelled = true }, onOpenDashboard = { opened = true })
        } }
        compose.onNodeWithText("Cancel connection").performClick()
        assertTrue(cancelled)
        assertFalse(opened)
        compose.runOnIdle { state = ConnectionState.Connected }
        compose.onNodeWithContentDescription("Scan for devices").assertDoesNotExist()
        compose.onNodeWithText("Open dashboard").performClick()
        assertTrue(opened)
    }

    @Test fun `configuration width selection is explicit and real numeric visualizations are offered`() {
        var columns by mutableStateOf(1)
        val widget = initial.widgets.single().widget
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, initial.copy(isEditing = true,
                widgets = listOf(initial.widgets.single().copy(widget = widget.copy(columnSpan = columns)))), "board", capabilities,
                actions = DashboardActions(resize = { id, width -> assertEquals(widget.id, id); columns = width }))
        } }
        configure()
        compose.onNodeWithText("Half").assertIsSelected()
        compose.onNodeWithText("Full").performClick()
        compose.onNodeWithText("Full").assertIsSelected().performClick()
        assertEquals(2, columns)
        compose.onNodeWithText("Value").assertIsSelected()
        compose.onNodeWithText("Gauge").assertExists()
        compose.onNodeWithText("Chart").assertExists()
        compose.onNodeWithText("Range").assertExists()
    }

    @Test fun `hide and restore keep original widget identity and ordering`() {
        var layout by mutableStateOf(defaultDashboard("board", capabilities))
        val original = layout.widgets.single()
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, dashboardState("board", capabilities, emptyMap(), layout).copy(isEditing = true),
                "board", capabilities, actions = DashboardActions(hide = { layout = layout.setWidgetHidden(it, true) },
                    restore = { layout = layout.setWidgetHidden(it, false) }))
        } }
        configure()
        compose.onNodeWithText("Hide widget").performScrollTo().performClick()
        assertTrue(layout.widgets.single().hidden)
        footer("Add widget")
        compose.onNodeWithText("Restore Temperature (1)").performClick()
        assertEquals(original, layout.widgets.single())
    }


    @Test fun `accessible reorder follows the selected widget stable identity`() {
        val metadata = capabilities.copy(capabilities = capabilities.capabilities + DeviceCapability("humidity", "567a", "%", "number"))
        var layout by mutableStateOf(defaultDashboard("board", metadata))
        val originalIds = layout.widgets.map { it.id }
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, dashboardState("board", metadata, emptyMap(), layout).copy(isEditing = true),
                "board", metadata, actions = DashboardActions(moveBy = { id, direction -> layout = layout.moveWidgetBy(id, direction) }))
        } }
        configure()
        compose.onNodeWithText("Move up").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Move down").performClick()
        assertEquals(originalIds.reversed(), layout.widgets.map { it.id })
        compose.onNodeWithText("Move down").assertIsNotEnabled()
        compose.onNodeWithText("Move up").performClick()
        assertEquals(originalIds, layout.widgets.map { it.id })
    }

    @Test fun `device switch dismisses previous configuration sheet`() {
        var device by mutableStateOf("board")
        var resizeCalls = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            val metadata = capabilities.copy(device = if (device == "board") "First project" else "Second project")
            DashboardContent(ConnectionState.Connected, dashboardState(device, metadata, emptyMap(), null).copy(isEditing = true),
                device, metadata, actions = DashboardActions(resize = { _, _ -> resizeCalls++ }))
        } }
        configure()
        compose.onNodeWithText("Widget settings").assertExists()
        compose.runOnIdle { device = "other-board" }
        compose.onNodeWithText("Widget settings").assertDoesNotExist()
        compose.onNodeWithText("Second project").assertIsDisplayed()
        assertEquals(0, resizeCalls)
    }

    @Test fun `live values update during editing without closing settings`() {
        var values by mutableStateOf(mapOf("temperature" to "12"))
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, dashboardState("board", capabilities, values, null).copy(isEditing = true),
                "board", capabilities)
        } }
        configure()
        compose.runOnIdle { values = mapOf("temperature" to "12345678901234567890") }
        compose.onNodeWithText("Widget settings").assertExists()
        compose.onNodeWithText("Close settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("12345678901234567890 \u00b0C").assertExists()
    }

    @Test fun `gauge settings validate bounds and apply real settings to the same widget`() {
        var configured: Triple<String, WidgetType, WidgetSettings>? = null
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, initial.copy(isEditing = true), "board", capabilities,
                actions = DashboardActions(configure = { id, type, settings -> configured = Triple(id, type, settings) }))
        } }
        configure()
        compose.onNodeWithText("Gauge").performScrollTo().performClick()
        compose.onNodeWithText("Apply settings").performScrollTo().performClick()
        compose.onNodeWithText("Set both minimum and maximum.").assertExists()
        assertNull(configured)
        compose.onNodeWithText("Minimum").performScrollTo().performTextInput("-10")
        compose.onNodeWithText("Maximum").performScrollTo().performTextInput("40")
        compose.onNodeWithText("Apply settings").performScrollTo().performClick()
        assertEquals(initial.widgets.single().widget.id, configured!!.first)
        assertEquals(WidgetType.GAUGE, configured!!.second)
        assertEquals(-10.0, configured!!.third.minimum!!, 0.0)
        assertEquals(40.0, configured!!.third.maximum!!, 0.0)
    }

    @Test fun `chart empty state never fabricates data and a real single sample has accessible axes`() {
        var points by mutableStateOf(emptyList<NumericSample>())
        val source = SensorSource("board", "temperature")
        val widget = initial.widgets.single().widget.copy(type = WidgetType.CHART, columnSpan = 2)
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            val history = SensorHistorySnapshot("board", 1, true, mapOf(source to SensorTimeline(points, 1000, 12.0, true)))
            DashboardContent(ConnectionState.Connected, dashboardState("board", capabilities, mapOf("temperature" to "12"),
                DashboardLayout(listOf(widget)), history, 1000, true), "board", capabilities)
        } }
        compose.onNodeWithText("Waiting for numeric samples in this time window.").assertExists()
        compose.runOnIdle { points = listOf(NumericSample(1000, 12.0, 0)) }
        compose.onNodeWithContentDescription("Line chart, 1 samples", substring = true).assertExists()
        compose.onNodeWithText("Live").assertExists()
        compose.onNodeWithText("Now").assertExists()
    }

    @Test fun `range shows actual units and explicitly flags a reading above its bounds`() {
        val source = SensorSource("board", "temperature")
        val widget = initial.widgets.single().widget.copy(type = WidgetType.RANGE,
            settings = WidgetSettings(minimum = -10.0, maximum = 40.0, upperThreshold = 30.0))
        val history = SensorHistorySnapshot("board", 1, true,
            mapOf(source to SensorTimeline(listOf(NumericSample(1000, 42.0, 0)), 1000, 42.0, true)))
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, dashboardState("board", capabilities, mapOf("temperature" to "42"),
                DashboardLayout(listOf(widget)), history, 20000, true), "board", capabilities)
        } }
        compose.onNodeWithText("42 \u00b0C").assertExists()
        compose.onNodeWithText("Above configured maximum").assertExists()
        compose.onNodeWithText("Stale - last update 19s ago").assertExists()
        compose.onNodeWithText("Live").assertDoesNotExist()
    }

    @Test fun `Try Demo opens simulated dashboard and Exit Demo returns without radio actions`() {
        var demo by mutableStateOf(false)
        var radioActions = 0
        val demoMetadata = com.dacodahub.physicallink.data.repository.DemoDeviceRepository.CAPABILITIES
        val demoId = com.dacodahub.physicallink.data.repository.DemoDeviceRepository.DEVICE_ID
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            if (!demo) DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle,
                permissionDenied = true, onTryDemo = { demo = true }, onScan = { radioActions++ }, onConnect = { radioActions++ })
            else DashboardContent(ConnectionState.Connected, dashboardState(demoId, demoMetadata, emptyMap(), null),
                demoId, demoMetadata, actions = DashboardActions(devices = { demo = false }, disconnect = { radioActions++ }), isDemo = true)
        } }
        compose.onNodeWithText("Try Demo").performClick()
        compose.onNodeWithText("Demo Sensor Station").assertExists()
        compose.onNodeWithText("Demo Mode - Simulated readings").assertExists()
        compose.onNodeWithText("These readings are simulated. Exit Demo to connect your own ESP32.").assertExists()
        compose.onNodeWithContentDescription("More device actions").assertDoesNotExist()
        compose.onNodeWithText("Exit Demo").performClick()
        compose.onNodeWithText("Try Demo").assertExists()
        assertEquals(0, radioActions)
    }

    @Test fun `demo entry remains available alongside a real active connection and editing remains available`() {
        var demo by mutableStateOf(false)
        var editing by mutableStateOf(false)
        var disconnected = 0
        val demoMetadata = com.dacodahub.physicallink.data.repository.DemoDeviceRepository.CAPABILITIES
        val demoId = com.dacodahub.physicallink.data.repository.DemoDeviceRepository.DEVICE_ID
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            if (!demo) DevicesContent(emptyList(), ConnectionState.Connected, ScanState.Idle, "real", capabilities,
                onTryDemo = { demo = true }, onCancel = { disconnected++ })
            else DashboardContent(ConnectionState.Connected, dashboardState(demoId, demoMetadata, emptyMap(), null).copy(isEditing = editing),
                demoId, demoMetadata, actions = DashboardActions(devices = { demo = false }, editing = { editing = it }), isDemo = true)
        } }
        compose.onNodeWithText("Try Demo").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit dashboard").performClick()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Exit Demo").assertExists()
        compose.onNodeWithContentDescription("Configure Temperature widget 1").performScrollTo().performClick()
        compose.onNodeWithText("Widget settings").assertExists()
        compose.onNodeWithText("Gauge").assertExists()
        compose.onNodeWithText("Close settings").performScrollTo().performClick()
        compose.onNodeWithText("Exit Demo").performClick()
        compose.onNodeWithText("Open dashboard").assertExists()
        assertEquals(0, disconnected)
    }

    @Test fun `timeout recovery is explicit and retry can be cancelled as a normal disconnect`() {
        var connection by mutableStateOf<ConnectionState>(ConnectionState.Error("Connection timed out"))
        var attempts = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), connection, ScanState.Idle, "board", onConnect = {
                assertEquals("board", it); attempts++; connection = ConnectionState.Connecting
            }, onCancel = { connection = ConnectionState.Disconnected })
        } }
        compose.onNodeWithText("Connection timed out").assertExists()
        assertEquals(0, attempts)
        compose.onNodeWithText("Retry connection").performScrollTo().performClick()
        assertEquals(1, attempts)
        compose.onNodeWithText("Retrying connection").assertExists()
        compose.onNodeWithText("Cancel connection").performScrollTo().performClick()
        compose.onNodeWithText("Retrying connection").assertDoesNotExist()
        compose.onNodeWithText("Project disconnected").assertDoesNotExist()
        assertEquals(1, attempts)
    }
    @Test fun `finished empty scan is distinguished from never scanning`() {
        var scan by mutableStateOf<ScanState>(ScanState.Idle)
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, scan)
        } }
        compose.onNodeWithText("No devices found yet").assertExists()
        compose.runOnIdle { scan = ScanState.Scanning }
        compose.runOnIdle { scan = ScanState.Idle }
        compose.onNodeWithText("No compatible devices found").assertExists()
    }

    @Test fun `friendly signal boundaries do not imply exact distance`() {
        assertEquals("Strong signal", signalStrengthLabel(-60))
        assertEquals("Good signal", signalStrengthLabel(-61))
        assertEquals("Good signal", signalStrengthLabel(-75))
        assertEquals("Weak signal", signalStrengthLabel(-76))
        assertEquals("Signal unavailable", signalStrengthLabel(0))
        assertEquals("Signal unavailable", signalStrengthLabel(127))
    }
}
