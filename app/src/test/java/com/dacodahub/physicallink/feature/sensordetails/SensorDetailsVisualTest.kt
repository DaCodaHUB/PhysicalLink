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
class SensorDetailsVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val manifest = SensorManifest("12345678-1234-1234-1234-123456789abc", "Workshop project", "0000000000000001",
        listOf(ManifestSensor("measurement.alpha", "Measurement Alpha", "u")))
    private val source = SensorSource(manifest.dashboardDeviceId(), "measurement.alpha")
    private fun fixture(case: String): SensorDetailsInput {
        val metadata = if (case == "long") manifest.copy(sensors = listOf(manifest.sensors.single().copy(
            name = "Long measurement channel \u0394 \u6E2C\u5B9A", unit = "widgets/(m\u00B2\u00B7s)"))) else manifest
        val samples = listOf(NumericSample(0, -2.5, 0), NumericSample(10000, -2.0, 0), NumericSample(20000, -3.0, 0),
            NumericSample(40000, -1.8, 0), NumericSample(59000, -1.2, 0))
        val timeline = SensorTimeline(samples, 59000, if (case == "unavailable") null else -1.2, case != "unavailable")
        return SensorDetailsInput(source.deviceId,
            if (case == "disconnected") null else if (case == "missing") metadata.copy(sensors = emptyList()).dashboardCapabilities() else metadata.dashboardCapabilities(),
            SensorHistorySnapshot(source.deviceId, 1, case != "disconnected", if (case == "empty") emptyMap() else mapOf(source to timeline)),
            if (case == "disconnected") ConnectionState.ConnectionLost else ConnectionState.Connected,
            mapOf(source.capabilityId to "-1.2"),
            if (case == "empty") LiveSensorState() else LiveSensorState(mapOf(source.capabilityId to SensorReading(0,
                if (case == "unavailable") null else -1.2, 5, 500, 59500)), true),
            if (case == "disconnected") null else metadata, if (case == "stale") 100000 else 60000)
    }
    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val file = File("build/reports/sensor-details/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun capture(dark: Boolean, large: Boolean = false) {
        var case by mutableStateOf("valid")
        var window by mutableIntStateOf(300)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                PhysicalLinkTheme(darkTheme = dark) { key(case) {
                    SensorDetailsContent(sensorDetailsState(source, fixture(case), window), onWindow = { window = it })
                } }
            }
        }
        listOf("valid", "empty", "stale", "unavailable", "long", "disconnected", "missing").forEach { selected ->
            compose.runOnIdle { case = selected }
            val name = "$selected-${if (dark) "dark" else "light"}${if (large) "-large-font" else ""}"
            compose.onNodeWithContentDescription("Back to dashboard").assertExists()
            if (selected == "stale") compose.onNodeWithText("Last known").assertExists()
            if (selected in listOf("empty", "unavailable", "disconnected", "missing")) compose.onNodeWithContentDescription("Measurement unavailable").assertExists()
            if (selected == "long") compose.onNodeWithContentDescription("-1.200 widgets/(m\u00B2\u00B7s)").assertExists()
            save(name)
            compose.onNodeWithTag("sensor-details-list").performScrollToIndex(3)
            save("$name-statistics")
            compose.onNodeWithTag("sensor-details-list").performScrollToIndex(4)
            compose.onAllNodesWithText(source.capabilityId).onLast().assertExists()
            val id = compose.onAllNodesWithText(source.capabilityId).onLast().getUnclippedBoundsInRoot()
            val nextLabel = compose.onNodeWithText("Display name").getUnclippedBoundsInRoot()
            assertTrue("Sensor information rows must not overlap", id.bottom <= nextLabel.top)
            save("$name-info")
        }
    }
    @Test fun light() = capture(false)
    @Test fun dark() = capture(true)
    @Test fun largeFontLight() = capture(false, true)
    @Test fun largeFontDark() = capture(true, true)
    @Test fun `windows are bounded and show honest empty statistics`() {
        var window by mutableIntStateOf(300)
        compose.setContent { PhysicalLinkTheme {
            SensorDetailsContent(sensorDetailsState(source, fixture("empty"), window), onWindow = { window = it })
        } }
        compose.onNodeWithText("15 min").performClick()
        assertEquals(900, window)
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("History statistics"))
        compose.onNodeWithText("No valid retained samples in this window.").assertExists()
    }
}
