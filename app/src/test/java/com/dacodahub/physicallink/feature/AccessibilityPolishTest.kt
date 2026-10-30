package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityPolishTest {
    @get:Rule val compose = createComposeRule()
    private val metadata = DeviceCapabilities(1, "Board", listOf(DeviceCapability("temperature", "5678", "C", "number")))
    @Test fun `chart current value and trend are readable on focus without a live region`() {
        val source = SensorSource("board", "temperature")
        val history = SensorHistorySnapshot("board", 1, true, mapOf(source to SensorTimeline(
            listOf(NumericSample(0, 10.0, 0), NumericSample(2000, 12.0, 0)), 2000, 12.0, true)))
        val layout = DashboardLayout(listOf(DashboardWidget("chart", WidgetType.CHART, source)))
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, dashboardState("board", metadata, mapOf("temperature" to "12"), layout, history, 2000))
        } }
        val chart = compose.onNodeWithContentDescription("Temperature. Line chart", substring = true)
        chart.assertExists().assert(SemanticsMatcher("Current value and trend") {
            val description = it.config[SemanticsProperties.ContentDescription].joinToString()
            description.contains("Current: 12") && description.contains("rose from 10 to 12")
        }).assert(SemanticsMatcher("No continuous live announcements") { !it.config.contains(SemanticsProperties.LiveRegion) })
    }
    @Test fun `editor has labeled touch targets and accessible reorder buttons`() {
        val layout = defaultDashboard("board", metadata).duplicateWidget(defaultDashboard("board", metadata).widgets.first().id, "copy")
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DashboardContent(ConnectionState.Connected, dashboardState("board", metadata, emptyMap(), layout).copy(isEditing = true))
        } }
        compose.onNodeWithContentDescription("Configure Temperature widget 1").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Move down").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Move up").assertIsNotEnabled()
    }
    @Test fun `large font bounds wrap onto separate rows instead of losing the maximum`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PhysicalLinkTheme(dynamicColor = false) {
                    Box { androidx.compose.material3.Surface(Modifier.width(160.dp)) {
                        val widget = DashboardWidget("range", WidgetType.RANGE, SensorSource("board", "temperature"),
                            settings = WidgetSettings(minimum = 123456789.0, maximum = 123456799.0))
                        DashboardWidgetCard(DashboardWidgetState(widget, metadata.capabilities.single(), "123456790",
                            SensorTimeline(lastSuccessfulMs = 0, latestNumeric = 123456790.0, latestValid = true), ReadingFreshness.LIVE))
                    } }
                }
            }
        }
        val minimum = compose.onNodeWithText("123456789").getUnclippedBoundsInRoot()
        val maximum = compose.onNodeWithText("123456799").getUnclippedBoundsInRoot()
        assertTrue("Maximum must wrap below minimum at large font scale: $minimum / $maximum", maximum.top >= minimum.bottom)
        assertTrue(maximum.right > maximum.left)
    }

    private fun contrast(dark: Boolean) {
        var colors: ColorScheme? = null
        compose.setContent { PhysicalLinkTheme(darkTheme = dark, dynamicColor = false) { colors = MaterialTheme.colorScheme } }
        compose.runOnIdle {
            val c = colors!!
            listOf(c.onSurface to c.background, c.onSurfaceVariant to c.surfaceContainerLow, c.primary to c.background,
                c.error to c.background, c.onErrorContainer to c.errorContainer).forEach { (foreground, background) ->
                val a = foreground.luminance(); val b = background.luminance()
                val ratio = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
                assertTrue("Small text contrast $ratio must be at least 4.5", ratio >= 4.5f)
            }
        }
    }
    @Test fun `static light theme semantic text colors meet normal text contrast`() = contrast(false)
    @Test fun `static dark theme semantic text colors meet normal text contrast`() = contrast(true)
}
