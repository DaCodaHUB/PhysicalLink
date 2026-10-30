package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.feature.devices.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiRefinementTest {
    @get:Rule val compose = createComposeRule()
    private val source = SensorSource("board", "temperature")
    private fun state(type: WidgetType = WidgetType.VALUE) = DashboardWidgetState(
        DashboardWidget("stable", type, source, columnSpan = 1), DeviceCapability("temperature", "5678", "C", "number"), "23.58",
        SensorTimeline(listOf(NumericSample(1000, 23.58, 0)), 1000, 23.58, true), ReadingFreshness.LIVE, 1000)

    private fun chartSelection(original: WidgetType, explicitHalf: Boolean = false): List<Int> {
        val widths = mutableListOf<Int>()
        var applied: WidgetType? = null
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            WidgetConfigurationSheet(state(original), "Board", 0, 1,
                DashboardActions(configure = { id, type, _ -> assertEquals("stable", id); applied = type },
                    resize = { id, columns -> assertEquals("stable", id); widths += columns }), {})
        } }
        compose.onNodeWithText("Chart").performScrollTo().performClick()
        compose.onNodeWithText(if (original == WidgetType.CHART) "Half" else "Full").assertIsSelected()
        if (explicitHalf) compose.onNodeWithText("Half").performScrollTo().performClick()
        compose.onNodeWithText("Apply settings").performScrollTo().performClick()
        assertEquals(WidgetType.CHART, applied)
        return widths
    }
    @Test fun `selecting a new Chart defaults full width`() { assertEquals(listOf(2), chartSelection(WidgetType.VALUE)) }
    @Test fun `applying settings to existing half Chart preserves width`() { assertEquals(emptyList<Int>(), chartSelection(WidgetType.CHART)) }
    @Test fun `explicit Half selection overrides new Chart default`() { assertEquals(listOf(1), chartSelection(WidgetType.VALUE, true)) }

    @Test fun `edit chart is compact with full title above accessible controls`() {
        var editing by mutableStateOf(false)
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            Box(Modifier.width(180.dp)) {
                ChartWidget(state(WidgetType.CHART), if (editing) WidgetControls(0, Modifier, true, {}) else null)
            }
        } }
        val chart = compose.onNodeWithContentDescription("Temperature. Line chart", substring = true)
        val normal = chart.getUnclippedBoundsInRoot()
        compose.onNodeWithText("Now").assertExists()
        compose.runOnIdle { editing = true }
        val compact = chart.getUnclippedBoundsInRoot()
        assertTrue(compact.bottom - compact.top < normal.bottom - normal.top)
        compose.onNodeWithText("Now").assertDoesNotExist()
        val title = compose.onNodeWithText("Temperature").getUnclippedBoundsInRoot()
        val control = compose.onNodeWithContentDescription("Drag Temperature widget", substring = true)
        assertTrue(title.bottom <= control.getUnclippedBoundsInRoot().top)
        compose.onNodeWithContentDescription("Configure Temperature widget 1").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Temperature").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow)
    }
    @Test fun `full chart retains readable time labels and current reading at double font scale`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PhysicalLinkTheme(dynamicColor = false) {
                    // Dashboard grid items have unbounded vertical space and scroll at large fonts.
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) { ChartWidget(state(WidgetType.CHART)) }
                }
            }
        }
        compose.onNodeWithContentDescription("23.58 \u00b0C").assertExists()
        compose.onNodeWithContentDescription("Temperature. Line chart", substring = true).assertWidthIsAtLeast(280.dp)
        listOf("1 min ago", "Now").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val result = layouts.single()
            // Wrap-content Text can retain a wider paragraph than its final box. Check
            // actual glyph bounds instead of treating that paragraph width as clipping.
            assertEquals(1, result.lineCount)
            assertFalse(result.isLineEllipsized(0))
            assertTrue("Time label $label must fit horizontally", result.getLineRight(0) <= result.size.width)
            assertTrue("Time label $label must fit vertically", result.getLineBottom(0) <= result.size.height)
        }
    }

    @Test fun `pressure reading and unit stay beside each other at large font scale`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                PhysicalLinkTheme(dynamicColor = false) {
                    Box(Modifier.width(300.dp)) { SensorReading("1004.99", "hPa") }
                }
            }
        }
        compose.onNodeWithContentDescription("1004.99 hPa").assertExists()
        val value = compose.onNodeWithText("1004.99", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val unit = compose.onNodeWithText("hPa", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(unit.left >= value.right)
        assertTrue(unit.top < value.bottom && unit.bottom > value.top)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("1004.99", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
    }
    @Test fun `explored Demo remains accessible without promotional panel or scan side effects`() {
        var demos = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, demoExplored = true,
                onTryDemo = { demos++ }, onScan = { fail("Demo must not scan") })
        } }
        compose.onNodeWithText("Explore without hardware").assertDoesNotExist()
        compose.onNodeWithTag("device-overview").performScrollToNode(hasText("Try Demo"))
        compose.onNodeWithText("Try Demo").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, demos)
        assertTrue(gettingStartedSections.none { it.second.contains("PhysicalLink Soil") })
        assertTrue(gettingStartedSections.any { it.second.contains("Arbitrary Bluetooth devices are not supported") })
    }
}
