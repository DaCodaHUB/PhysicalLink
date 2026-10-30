package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.domain.model.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SprintThreeReadabilityTest {
    @get:Rule val compose = createComposeRule()
    private val unit = "widgets/(m\u00B2\u00B7s)"
    private val title = "Long channel \u0394 \u6E2C\u5B9A measurement"
    private fun fixture(type: WidgetType) = DashboardWidgetState(
        DashboardWidget("id", type, SensorSource("board", "channel"), settings = WidgetSettings(minimum = -20.0, maximum = 20.0)),
        DeviceCapability("channel", "fixture", unit, "number", title), "12.34",
        SensorTimeline(listOf(NumericSample(0, 10.0, 0), NumericSample(1000, 12.34, 0)), 1000, 12.34, true), ReadingFreshness.LIVE, 20000)
    private fun availability(dark: Boolean) {
        var state by mutableStateOf(fixture(WidgetType.VALUE))
        compose.setContent { PhysicalLinkTheme(darkTheme = dark) {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { DashboardWidgetCard(state) }
        } }
        WidgetType.entries.forEach { type ->
            compose.runOnIdle { state = fixture(type) }
            compose.onNodeWithContentDescription("12.34 $unit").assertExists()
            compose.runOnIdle { state = state.copy(freshness = ReadingFreshness.STALE) }
            compose.onNodeWithText("Last known").assertExists()
            compose.onNodeWithContentDescription("Last known: 12.34 $unit").assertExists()
            compose.onNodeWithContentDescription("12.34 $unit").assertDoesNotExist()
            compose.runOnIdle { state = state.copy(freshness = ReadingFreshness.UNAVAILABLE) }
            compose.onNodeWithContentDescription("Measurement unavailable").assertExists()
            compose.onNodeWithContentDescription("Last known: 12.34 $unit").assertDoesNotExist()
            compose.onNodeWithContentDescription("12.34 $unit").assertDoesNotExist()
            compose.onNodeWithText("Last known").assertDoesNotExist()
            if (type == WidgetType.CHART) {
                compose.onNodeWithText("Recorded history").assertExists()
                val historyLabel = compose.onNodeWithText("Recorded history").getUnclippedBoundsInRoot()
                val upperAxis = compose.onNodeWithText("12.34 $unit").getUnclippedBoundsInRoot()
                assertTrue("Historical label and axis must not overlap", upperAxis.top >= historyLabel.bottom)
                compose.onNodeWithContentDescription("Current value unavailable", substring = true).assertExists()
            } else if (type == WidgetType.GAUGE || type == WidgetType.RANGE) {
                compose.onNodeWithContentDescription("Within configured bounds").assertDoesNotExist()
            }
        }
    }
    private fun largeUnits(dark: Boolean) {
        var type by mutableStateOf(WidgetType.VALUE)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PhysicalLinkTheme(darkTheme = dark) {
                    Column(Modifier.width(180.dp).verticalScroll(rememberScrollState())) { DashboardWidgetCard(fixture(type)) }
                }
            }
        }
        WidgetType.entries.forEach { widgetType ->
            compose.runOnIdle { type = widgetType }
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithContentDescription("12.34 $unit").assertExists()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(unit, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val text = layouts.single()
            assertTrue(text.lineCount in 1..3)
            assertEquals(unit, text.layoutInput.text.text)
            assertTrue(text.size.width <= 148) // 180dp card less 16dp padding per side, mdpi.
        }
    }
    @Test fun lightAvailability() = availability(false)
    @Test fun darkAvailability() = availability(true)
    @Test fun largeLightUnits() = largeUnits(false)
    @Test fun largeDarkUnits() = largeUnits(true)
}
