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
            if (case == "normal") {
                compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Decimal places (0-6)"))
                compose.onNodeWithText("Decimal places (0-6)").performTextReplacement("3")
                compose.onNodeWithText("Apply settings").performScrollTo().assertIsEnabled()
                compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText(if (type == WidgetType.VALUE) "Display precision" else "Visualization settings"))
                compose.onNodeWithText("Apply settings").assertIsDisplayed()
                save("$name-editing")
                compose.onNodeWithText("Cancel").performClick()
            }
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
        compose.setContent { PhysicalLinkTheme(darkTheme = dark) {
            SensorDetailsContent(sensorDetailsState(source, input("normal"), configuredWidget = widget(WidgetType.GAUGE)))
        } }
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
        compose.onNodeWithText("Minimum").performTextReplacement("not a number")
        compose.onNodeWithText("Apply settings").performScrollTo().assertIsNotEnabled()
        save("inline-invalid-${if (dark) "dark" else "light"}")
    }
    @Test fun `bounded previews change position immediately and cancel restores each presentation`() {
        var type by mutableStateOf(WidgetType.GAUGE)
        var saves = 0
        compose.setContent { PhysicalLinkTheme { key(type) {
            SensorDetailsContent(sensorDetailsState(source, input("normal"), configuredWidget = widget(type)), onApply = { saves++ })
        } } }
        listOf(WidgetType.GAUGE, WidgetType.RANGE).forEach { selected ->
            compose.runOnIdle { type = selected }
            compose.onNodeWithContentDescription("Within configured bounds").assertExists()
            compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
            compose.onNodeWithText("Minimum").performTextReplacement("0")
            compose.onNodeWithTag("sensor-details-list").performScrollToIndex(0)
            compose.onNodeWithContentDescription("Below configured minimum").assertExists()
            assertEquals(0, saves)
            compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Cancel"))
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("sensor-details-list").performScrollToIndex(0)
            compose.onNodeWithContentDescription("Within configured bounds").assertExists()
        }
    }
    @Test fun `chart previews custom axes and reset without saving draft`() {
        var saved: WidgetSettings? = null
        compose.setContent { PhysicalLinkTheme {
            SensorDetailsContent(sensorDetailsState(source, input("normal"), configuredWidget = widget(WidgetType.CHART)), onApply = { saved = it })
        } }
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Chart minimum"))
        compose.onNodeWithText("Chart minimum").performTextReplacement("-0.5")
        compose.onNodeWithTag("sensor-details-list").performScrollToIndex(2)
        compose.onNodeWithContentDescription("Value axis -0.5000", substring = true).assertExists()
        assertNull(saved)
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Custom maximum"))
        compose.onNodeWithText("Custom maximum").performClick()
        compose.onNodeWithText("Chart maximum").performScrollTo().performTextReplacement("-1")
        compose.onNodeWithText("Apply settings").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Reset axes to Auto").performScrollTo().performClick()
        compose.onNodeWithText("Apply settings").performScrollTo().performClick()
        assertNull(saved!!.chartMinimum)
        assertNull(saved!!.chartMaximum)
        assertEquals(4, saved!!.precision)
    }
    @Test fun light() = capture(false)
    @Test fun dark() = capture(true)
    @Test fun invalidLight() = invalidSettings(false)
    @Test fun invalidDark() = invalidSettings(true)
}
