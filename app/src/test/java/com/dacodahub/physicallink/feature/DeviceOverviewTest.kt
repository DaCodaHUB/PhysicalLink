package com.dacodahub.physicallink.feature

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.devices.*
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceOverviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val metadata = DeviceCapabilities(2, "My project \u0394", listOf(DeviceCapability("channel", "fixture", "u", "number")))
    private fun scroll(text: String) { compose.onNodeWithTag("device-overview").performScrollToNode(hasText(text)) }
    private fun save(name: String) {
        val file = File("build/reports/ui-sprint-4/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun captures(dark: Boolean, large: Boolean = false) {
        var screen by mutableStateOf("empty")
        val states = listOf("empty", "connected", "scanning", "scanning-results", "results", "connecting", "disconnected", "bluetooth", "permission", "error")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                PhysicalLinkTheme(darkTheme = dark) { key(screen) {
                    val connection = when (screen) {
                        "connected" -> ConnectionState.Connected
                        "connecting" -> ConnectionState.Discovering
                        "disconnected" -> ConnectionState.ConnectionLost
                        "error" -> ConnectionState.Error("Connection timed out")
                        else -> ConnectionState.Disconnected
                    }
                    DevicesContent(
                        devices = if (screen == "connected" || screen == "results" || screen == "scanning-results") listOf(DiscoveredDevice("Workshop project", "fixture", -62)) else emptyList(),
                        connection = connection,
                        scan = when (screen) { "scanning", "scanning-results" -> ScanState.Scanning; "bluetooth" -> ScanState.Error("Bluetooth is disabled"); else -> ScanState.Idle },
                        deviceId = "fixture", capabilities = metadata, permissionDenied = screen == "permission",
                        onDiagnostic = if (screen == "connected") ({}) else null)
                } }
            }
        }
        states.forEach { state ->
            compose.runOnIdle { screen = state }
            compose.onNodeWithContentDescription("Appearance").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            if (state == "connected") {
                compose.onNodeWithText(metadata.device).assertExists()
                compose.onNodeWithText("1 sensor", substring = true).assertExists()
                compose.onNodeWithText("Open dashboard").assertExists()
            } else {
                compose.onNodeWithText("Open dashboard").assertDoesNotExist()
                compose.onNodeWithText(metadata.device).assertDoesNotExist()
                compose.onNodeWithText("1 sensor", substring = true).assertDoesNotExist()
            }
            save("$state-${if (dark) "dark" else "light"}${if (large) "-large-font" else ""}")
            scroll("Try Demo")
            compose.onNodeWithText("Try Demo").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("simulated readings", substring = true).assertExists()
            if (large || state == "connected" || state == "scanning") save("$state-explore-${if (dark) "dark" else "light"}${if (large) "-large-font" else ""}")
        }
    }
    @Test fun lightStates() = captures(false)
    @Test fun darkStates() = captures(true)
    @Test fun largeFontLightStates() = captures(false, true)
    @Test fun largeFontDarkStates() = captures(true, true)
    @Test fun `finding another device requires explicit disconnect and never starts a scan implicitly`() {
        var scans = 0; var disconnects = 0; var dashboards = 0; var diagnostics = 0; var demos = 0
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(emptyList(), ConnectionState.Connected, ScanState.Idle, capabilities = metadata,
                onScan = { scans++ }, onCancel = { disconnects++ }, onTryDemo = { demos++ },
                onOpenDashboard = { dashboards++ }, onDiagnostic = { diagnostics++ }, showIntroduction = true)
        } }
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
        compose.onNodeWithText("Open dashboard").performClick()
        compose.onNodeWithText("Device Diagnostics").performClick()
        assertEquals(1, dashboards); assertEquals(1, diagnostics)
        scroll("Find Devices")
        compose.onNode(hasText("Find Devices") and hasClickAction()).performClick()
        compose.onNodeWithText("Find another device").assertExists()
        assertEquals(0, disconnects); assertEquals(0, scans)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Find another device").assertDoesNotExist()
        compose.onNode(hasText("Find Devices") and hasClickAction()).performClick()
        compose.onNodeWithText("Disconnect device").performClick()
        assertEquals(1, disconnects); assertEquals(0, scans)
        scroll("Try Demo")
        compose.onNodeWithText("Try Demo").performClick()
        assertEquals(1, demos); assertEquals(1, disconnects); assertEquals(0, scans)
    }
    @Test fun `scan starts and stops only after action and selects a previously seen device`() {
        var scan by mutableStateOf<ScanState>(ScanState.Idle)
        var starts = 0; var stops = 0; var selected: String? = null
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(listOf(DiscoveredDevice("Workshop", "address", -70)), ConnectionState.Disconnected, scan,
                onScan = { starts++; scan = ScanState.Scanning }, onStopScan = { stops++; scan = ScanState.Idle }, onConnect = { selected = it })
        } }
        assertEquals(0, starts)
        scroll("Scan for devices")
        compose.onNodeWithContentDescription("Scan for devices").performClick()
        assertEquals(1, starts)
        compose.onNodeWithContentDescription("Stop scanning").performClick()
        assertEquals(1, stops)
        scroll("Workshop")
        compose.onNodeWithText("Seen in the last scan. Availability may change.").assertExists()
        compose.onNodeWithTag("device:address").performClick()
        assertEquals("address", selected)
    }
    @Test fun `other observed devices cannot replace an active session without disconnecting`() {
        var connects = 0
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(listOf(DiscoveredDevice("Other project", "other", -70)), ConnectionState.Connected, ScanState.Idle,
                deviceId = "active", capabilities = metadata, onConnect = { connects++ })
        } }
        scroll("Other project")
        compose.onNodeWithText("Not connected").assertExists()
        compose.onNodeWithTag("device:other").assertIsNotEnabled().performClick()
        assertEquals(0, connects)
        compose.onNodeWithContentDescription("Details for Other project").performClick()
        compose.onNodeWithText("-70 dBm", substring = true).assertExists()
        assertEquals(0, connects)
    }
    @Test fun `permission recovery is explicit and metadata count is omitted until available`() {
        var opens = 0; var scans = 0
        var connected by mutableStateOf(false)
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(emptyList(), if (connected) ConnectionState.Connected else ConnectionState.Disconnected,
                ScanState.Idle, permissionDenied = true, onSettings = { opens++ }, onScan = { scans++ })
        } }
        assertEquals(0, opens); assertEquals(0, scans)
        scroll("Open app settings")
        compose.onNodeWithText("Open app settings").performClick()
        assertEquals(1, opens); assertEquals(0, scans)
        compose.runOnIdle { connected = true }
        scroll("Open dashboard")
        compose.onNodeWithText("sensors", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 sensor", substring = true).assertDoesNotExist()
    }
}
