package com.dacodahub.physicallink.feature

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dacodahub.physicallink.core.designsystem.theme.*
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.feature.devices.DevicesContent
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Explicit test fixtures only; these samples never enter application repositories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SprintVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun save(name: String) {
        val file = File("build/reports/ui-sprint/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun dashboard(dark: Boolean, large: Boolean = false) {
        fun state(type: WidgetType, id: String, label: String, unit: String, value: Double) = DashboardWidgetState(
            DashboardWidget(id, type, SensorSource("fixture", id), columnSpan = if (type == WidgetType.CHART || type == WidgetType.RANGE) 2 else 1,
                settings = WidgetSettings(minimum = -10.0, maximum = 40.0)),
            DeviceCapability(id, "fixture-$id", unit, "number", label), value.toString(),
            SensorTimeline((0..60).map { NumericSample(it * 1000L, value + (it % 10) * 0.1, 0) }, 60000, value, true),
            ReadingFreshness.LIVE, 60000)
        val widgets = listOf(
            state(WidgetType.CHART, "a", if (large) "Long arbitrary project measurement channel" else "Sensor A", if (large) "widgets/(m²·s)" else "u", 3.72),
            state(WidgetType.VALUE, "b", "Sensor B", "custom", -0.0084),
            state(WidgetType.GAUGE, "c", "Sensor C", "u", 21.6),
            state(WidgetType.RANGE, "d", "Sensor D", "u", 12.0))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                PhysicalLinkTheme(darkTheme = dark) {
                    DashboardContent(ConnectionState.Connected, DashboardState(deviceName = "My project", discovering = false, widgets = widgets))
                }
            }
        }
        compose.onNodeWithText("Now").assertExists()
        val name = if (large) "large-font" else if (dark) "dark" else "light"
        save(name)
        compose.onNodeWithTag("dashboard-grid").performScrollToIndex(3)
        compose.onNodeWithText("Sensor D").assertExists()
        save("$name-range")
    }
    private fun devices(dark: Boolean) {
        val capabilities = DeviceCapabilities(1, "My project", listOf(DeviceCapability("channel", "fixture", "u", "number")))
        compose.setContent { PhysicalLinkTheme(darkTheme = dark) {
            DevicesContent(listOf(DiscoveredDevice("My project", "fixture", -62)), ConnectionState.Connected, ScanState.Idle,
                deviceId = "fixture", capabilities = capabilities, onDiagnostic = {}, showIntroduction = true)
        } }
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
        compose.onNodeWithText("Open dashboard").assertIsDisplayed()
        compose.onNodeWithText("Disconnect").assertExists()
        save(if (dark) "devices-dark" else "devices-light")
    }
    @Test fun light() = dashboard(false)
    @Test fun dark() = dashboard(true)
    @Test fun largeFont() = dashboard(false, true)
    @Test fun devicesLight() = devices(false)
    @Test fun devicesDark() = devices(true)
    @Test fun `accent assignment is stable and bounded for opaque identities`() {
        listOf("", "arbitrary-id", "-2147483648", "測定").forEach {
            assertTrue(widgetAccentIndex(it) in 0..2)
            assertEquals(widgetAccentIndex(it), widgetAccentIndex(it))
        }
    }
}
