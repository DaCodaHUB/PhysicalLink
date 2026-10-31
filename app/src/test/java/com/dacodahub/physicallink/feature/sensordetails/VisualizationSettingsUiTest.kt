package com.dacodahub.physicallink.feature.sensordetails

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import com.dacodahub.physicallink.FakeDashboardLayoutRepository
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualizationSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val repository = DetailsWidgetRepository()
    private val layouts = FakeDashboardLayoutRepository()
    private val store = ViewModelStore()
    private lateinit var vm: DashboardViewModel
    @Before fun setup() {
        runBlocking { layouts.update(repository.source.deviceId) { repository.layout() }.await() }
        compose.runOnIdle { vm = DashboardViewModel(repository, layouts, SavedStateHandle()); store.put("vm", vm) }
    }
    @After fun clear() { compose.runOnIdle { store.clear() } }
    @Test fun `details resolve duplicate instances and save validated settings only to the selected widget`() {
        var target by mutableStateOf(SensorDetailsTarget(repository.source, "gauge"))
        compose.setContent { PhysicalLinkTheme { key(target.widgetId) { SensorDetailsScreen(target, {}, vm) } } }
        compose.onNodeWithText("Gauge details").assertExists()
        compose.onNodeWithText("5 min").assertDoesNotExist()
        compose.onNodeWithContentDescription("12.345 \u03BCg").assertExists()
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
        compose.onNodeWithText("Minimum").performScrollTo().performTextReplacement("NaN")
        compose.onNodeWithText("Apply settings").performScrollTo().assertIsNotEnabled()
        assertEquals(repository.layout(), runBlocking { layouts.observe(repository.source.deviceId).first() })
        compose.onNodeWithText("Minimum").performScrollTo().performTextReplacement("-20")
        compose.onNodeWithText("Apply settings").performScrollTo().performClick()
        compose.runOnIdle { target = target.copy(widgetId = "chart") }
        compose.onNodeWithText("Chart details").assertExists()
        compose.onNodeWithText("5 min").assertExists()
        compose.onNodeWithContentDescription("12.3 \u03BCg").assertExists()
        val saved = runBlocking { layouts.observe(repository.source.deviceId).first()!! }
        assertEquals(-20.0, saved.widgets.first().settings.minimum!!, 0.0)
        assertEquals(repository.layout().widgets.last(), saved.widgets.last())
    }
    @Test fun `deleting a widget while details and settings are open closes the editor and disables saving`() {
        compose.setContent { PhysicalLinkTheme { SensorDetailsScreen(SensorDetailsTarget(repository.source, "gauge"), {}, vm) } }
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
        compose.onNodeWithText("Minimum").performTextReplacement("-20")
        compose.onNodeWithText("Apply settings").assertExists()
        compose.runOnIdle { layouts.update(repository.source.deviceId) { it!!.copy(widgets = it.widgets.filterNot { widget -> widget.id == "gauge" }) } }
        compose.onNodeWithText("Widget no longer available").assertExists()
        compose.onNodeWithText("Apply settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Visualization settings").assertDoesNotExist()
    }
    @Test fun `inline precision previews without persistence and cancel restores saved reading`() {
        compose.setContent { PhysicalLinkTheme { SensorDetailsScreen(SensorDetailsTarget(repository.source, "gauge"), {}, vm) } }
        compose.onNodeWithContentDescription("Visualization settings").assertDoesNotExist()
        compose.onNodeWithText("Edit bounds and precision").assertDoesNotExist()
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Decimal places (0-6)"))
        compose.onNodeWithText("Decimal places (0-6)").performTextReplacement("1")
        compose.onNodeWithTag("sensor-details-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("12.3 \u03BCg").assertExists()
        assertEquals(repository.layout(), runBlocking { layouts.observe(repository.source.deviceId).first() })
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Cancel"))
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("sensor-details-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("12.345 \u03BCg").assertExists()
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
        listOf("-", "1e-", "Infinity", "NaN", "100").forEach { invalid ->
            compose.onNodeWithText("Minimum").performTextReplacement(invalid)
            compose.onNodeWithText("Apply settings").performScrollTo().assertIsNotEnabled()
            assertEquals(repository.layout(), runBlocking { layouts.observe(repository.source.deviceId).first() })
            compose.onNodeWithText("Minimum").performScrollTo()
        }
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(repository.layout(), runBlocking { layouts.observe(repository.source.deviceId).first() })
    }
    @Test fun `leaving details discards unapplied drafts and reopening uses the saved widget`() {
        var showing by mutableStateOf(true)
        compose.setContent { PhysicalLinkTheme {
            if (showing) SensorDetailsScreen(SensorDetailsTarget(repository.source, "gauge"), { showing = false }, vm)
        } }
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
        compose.onNodeWithText("Minimum").performTextReplacement("-2e-6")
        compose.onNodeWithContentDescription("Back to dashboard").performClick()
        assertEquals(repository.layout(), runBlocking { layouts.observe(repository.source.deviceId).first() })
        compose.runOnIdle { showing = true }
        compose.onNodeWithTag("sensor-details-list").performScrollToNode(hasText("Minimum"))
        compose.onNodeWithText("Minimum").assertTextContains("0.0")
        compose.onNodeWithText("Apply settings").assertDoesNotExist()
    }
    @Test fun `dashboard numeric fields reject pasted text and preserve configuration until valid apply`() {
        var saved: WidgetSettings? = null
        val widget = repository.layout().widgets.first()
        val state = DashboardWidgetState(widget, repository.capabilities.value!!.capabilities.single(), "12.345")
        compose.setContent { PhysicalLinkTheme {
            WidgetConfigurationSheet(state, "Project", 0, 1, DashboardActions(configure = { _, _, settings -> saved = settings }), {})
        } }
        compose.onNodeWithText("Minimum").performScrollTo().performTextReplacement("pasted invalid text")
        compose.onNodeWithText("Apply settings").performScrollTo().assertIsNotEnabled()
        assertNull(saved)
        compose.onNodeWithText("Minimum").performScrollTo().performTextReplacement("-1e300")
        compose.onNodeWithText("Maximum").performScrollTo().performTextReplacement("1e300")
        compose.onNodeWithText("Apply settings").performScrollTo().performClick()
        assertEquals(-1e300, saved!!.minimum!!, 0.0)
        assertEquals(1e300, saved!!.maximum!!, 0.0)
    }
    @Test fun `custom chart bounds are independently validated and reset to Auto without losing range settings`() {
        var saved: WidgetSettings? = null
        val widget = repository.layout().widgets.last().copy(settings = WidgetSettings(minimum = -10.0, maximum = 10.0))
        compose.setContent { PhysicalLinkTheme { VisualizationSettingsSheet(widget, { saved = it }, {}) } }
        compose.onNodeWithText("Custom minimum").performScrollTo().performClick()
        compose.onNodeWithText("Save settings").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Chart minimum").performScrollTo().performTextInput("-2e-6")
        compose.onNodeWithText("Custom maximum").performScrollTo().performClick()
        compose.onNodeWithText("Chart maximum").performScrollTo().performTextInput("bad pasted value")
        compose.onNodeWithText("Save settings").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Chart maximum").performScrollTo().performTextReplacement("1e300")
        compose.onNodeWithText("Save settings").performScrollTo().performClick()
        assertEquals(-2e-6, saved!!.chartMinimum!!, 0.0)
        assertEquals(1e300, saved!!.chartMaximum!!, 0.0)
        compose.onNodeWithText("Reset axes to Auto").performScrollTo().performClick()
        compose.onNodeWithText("Save settings").performScrollTo().performClick()
        assertNull(saved!!.chartMinimum); assertNull(saved!!.chartMaximum)
        assertEquals(widget.settings.minimum, saved!!.minimum)
    }
}
