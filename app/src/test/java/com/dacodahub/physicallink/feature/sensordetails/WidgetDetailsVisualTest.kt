package com.dacodahub.physicallink.feature.sensordetails

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.VisualizationSettingsSheet
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
class WidgetDetailsVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val manifest = SensorManifest("12345678-1234-1234-1234-123456789abc", "Workshop project", "0000000000000001",
        listOf(ManifestSensor("channel", "Measurement Alpha", "u")))
    private val source = SensorSource(manifest.dashboardDeviceId(), "channel")
    private fun input(case: String): SensorDetailsInput {
        val metadata = if (case == "long") manifest.copy(sensors = listOf(manifest.sensors.single().copy(
            name = "Long numeric channel \u0394 \u6E2C\u5B9A", unit = "widgets/(m\u00B2\u00B7s)"))) else manifest
        val values = listOf(-.012, -.0084, -.01, .005, -.0084)
        val samples = values.mapIndexed { index, value -> NumericSample(index * 14000L, value, 0) }
        val timeline = SensorTimeline(samples, 56000, if (case == "unavailable") null else -.0084, case != "unavailable")
        return SensorDetailsInput(source.deviceId, metadata.dashboardCapabilities(), SensorHistorySnapshot(source.deviceId, 1, true, mapOf(source to timeline)),
            ConnectionState.Connected, mapOf("channel" to "-0.0084"), LiveSensorState(mapOf("channel" to SensorReading(0,
                if (case == "unavailable") null else -.0084, 5, 0, 56000)), true), metadata, if (case == "stale") 100000 else 57000)
    }
    private fun widget(type: WidgetType) = DashboardWidget(type.name.lowercase(), type, source,
        settings = WidgetSettings(chartWindowSeconds = 300, minimum = if (type == WidgetType.GAUGE || type == WidgetType.RANGE) -.02 else null,
            maximum = if (type == WidgetType.GAUGE || type == WidgetType.RANGE) .02 else null,
            precision = if (type == WidgetType.VALUE) 6 else 4, staleAfterSeconds = 30,
            chartMinimum = if (type == WidgetType.CHART) -.01 else null))
    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
        val file = File("build/reports/sensor-details-2/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val dialogView = if (name.startsWith("invalid-settings")) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView else null
            val view = dialogView ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun capture(dark: Boolean) {
        var type by mutableStateOf(WidgetType.VALUE)
        var case by mutableStateOf("normal")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (case == "long") 1.6f else 1f)) {
                PhysicalLinkTheme(darkTheme = dark) { key(type, case) {
                    SensorDetailsContent(sensorDetailsState(source, input(case), configuredWidget = widget(type)))
                } }
            }
        }
        WidgetType.entries.forEach { selectedType -> listOf("normal", "stale", "unavailable", "long").forEach { selectedCase ->
            compose.runOnIdle { type = selectedType; case = selectedCase }
            val name = "${selectedType.name.lowercase()}-$selectedCase-${if (dark) "dark" else "light"}"
            compose.onNodeWithText("${selectedType.name.lowercase().replaceFirstChar { it.titlecase() }} details").assertExists()
            if (type != WidgetType.CHART) compose.onNodeWithText("Reading history").assertDoesNotExist()
            if (case == "unavailable") compose.onNodeWithContentDescription("Measurement unavailable").assertExists()
            save(name)
            if (type == WidgetType.CHART) {
                compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("History statistics"))
                compose.onNodeWithText("5 valid samples").assertExists()
                save("$name-statistics")
            }
            compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Sensor information"))
            save("$name-info")
        } }
    }
    private fun invalidSettings(dark: Boolean) {
        compose.setContent { PhysicalLinkTheme(darkTheme = dark) { VisualizationSettingsSheet(widget(WidgetType.GAUGE), {}, {}) } }
        compose.onNodeWithText("Minimum").performScrollTo().performTextReplacement("not a number")
        compose.onNodeWithText("Save settings").performScrollTo().assertIsNotEnabled()
        save("invalid-settings-${if (dark) "dark" else "light"}")
    }
    @Test fun light() = capture(false)
    @Test fun dark() = capture(true)
    @Test fun invalidLight() = invalidSettings(false)
    @Test fun invalidDark() = invalidSettings(true)
}
