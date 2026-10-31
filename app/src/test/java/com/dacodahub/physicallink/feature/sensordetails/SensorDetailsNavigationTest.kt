package com.dacodahub.physicallink.feature.sensordetails

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.dashboard.*
import com.dacodahub.physicallink.navigation.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SensorDetailsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val source = SensorSource("stable:device/one?", "arbitrary.channel%/one")
    private fun widget(type: WidgetType, id: String = "widget", selected: SensorSource = source) = DashboardWidgetState(
        DashboardWidget(id, type, selected, columnSpan = 1, settings = WidgetSettings(minimum = -10.0, maximum = 50.0)),
        DeviceCapability(selected.capabilityId, "fixture", "u", "number", "Sensor Alpha"), "12",
        SensorTimeline(listOf(NumericSample(0, 10.0, 0), NumericSample(1000, 12.0, 0)), 1000, 12.0, true), ReadingFreshness.LIVE, 1000)
    @Test fun `all four widgets and duplicates open the stable identity route while editing never navigates`() {
        var type by mutableStateOf(WidgetType.VALUE)
        var editing by mutableStateOf(false)
        var opened: SensorDetailsTarget? = null
        lateinit var nav: NavHostController
        compose.setContent { PhysicalLinkTheme {
            nav = rememberNavController()
            PhysicalLinkNavContent(nav, devices = { open, _ -> Button(onClick = open) { Text("Open project") } },
                dashboard = { _, details -> DashboardContent(ConnectionState.Connected,
                    DashboardState("Project", discovering = false, widgets = listOf(widget(type), widget(type, "copy")), isEditing = editing),
                    actions = DashboardActions(sensorDetails = details, editing = { editing = it })) },
                sensorDetails = { selected, _, back ->
                    SideEffect { opened = selected }
                    Button(onClick = back) { Text("Back to dashboard") }
                })
        } }
        compose.onNodeWithText("Open project").performClick()
        WidgetType.entries.forEach { selected ->
            compose.runOnIdle { type = selected; opened = null }
            compose.onNodeWithTag("sensor-widget:widget").performClick()
            compose.onNodeWithText("Back to dashboard").assertExists()
            compose.runOnIdle {
                assertEquals(source, opened?.source)
                assertEquals("widget", opened?.widgetId)
                assertEquals(source.deviceId, nav.currentBackStackEntry!!.arguments!!.getString("deviceId"))
                assertEquals(source.capabilityId, nav.currentBackStackEntry!!.arguments!!.getString("sensorId"))
            }
            compose.onNodeWithText("Back to dashboard").performClick()
        }
        compose.runOnIdle { type = WidgetType.VALUE; opened = null }
        compose.onNodeWithTag("sensor-widget:copy").performClick()
        compose.runOnIdle { assertEquals(source, opened?.source); assertEquals("copy", opened?.widgetId) }
        compose.onNodeWithText("Back to dashboard").performClick()
        compose.onNodeWithContentDescription("Edit dashboard").performClick()
        compose.onNodeWithTag("sensor-widget:widget").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Configure Sensor Alpha widget 1").performClick()
        compose.onNodeWithText("Widget settings").assertExists()
        compose.runOnIdle { assertEquals("dashboard?demo={demo}", nav.currentDestination!!.route) }
    }
    @Test fun `return restores dashboard scroll position without changing full and half sizes`() {
        lateinit var nav: NavHostController
        val widgets = (0..24).map { i -> widget(WidgetType.VALUE, "id$i", source.copy(capabilityId = "channel.$i"))
            .let { it.copy(widget = it.widget.copy(columnSpan = if (i % 2 == 0) 2 else 1)) } }
        compose.setContent { PhysicalLinkTheme {
            nav = rememberNavController()
            PhysicalLinkNavContent(nav, devices = { open, _ -> Button(onClick = open) { Text("Open project") } },
                dashboard = { _, details -> DashboardContent(ConnectionState.Connected, DashboardState("Project", false, widgets),
                    actions = DashboardActions(sensorDetails = details)) },
                sensorDetails = { _, _, back -> Button(onClick = back) { Text("Back to dashboard") } })
        } }
        compose.onNodeWithText("Open project").performClick()
        compose.onNodeWithTag("dashboard-grid").performScrollToIndex(18)
        val before = compose.onNodeWithTag("sensor-widget:id18").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("sensor-widget:id18").performClick()
        compose.onNodeWithText("Back to dashboard").performClick()
        val after = compose.onNodeWithTag("sensor-widget:id18").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(before.top.value, after.top.value, 1f)
        assertEquals((before.right - before.left).value, (after.right - after.left).value, 1f)
        assertEquals(listOf(2, 1), widgets.take(2).map { it.widget.columnSpan })
    }
    @Test fun `details navigation restores stable arguments and retains the demo parent`() {
        val restoration = StateRestorationTester(compose)
        lateinit var nav: NavHostController
        restoration.setContent { PhysicalLinkTheme {
            nav = rememberNavController()
            PhysicalLinkNavContent(nav, devices = { _, demo -> Button(onClick = demo) { Text("Open demo") } },
                dashboard = { _, details -> Button(onClick = { details(SensorDetailsTarget(source, "widget")) }) { Text("Sensor") } },
                sensorDetails = { selected, parent, back ->
                    Text(selected.source.capabilityId)
                    Text(if (parent.arguments!!.getBoolean("demo")) "Demo parent" else "Real parent")
                    Button(onClick = back) { Text("Back to dashboard") }
                })
        } }
        compose.onNodeWithText("Open demo").performClick()
        compose.onNodeWithText("Sensor").performClick()
        compose.onNodeWithText("Demo parent").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(source.capabilityId).assertExists()
        compose.onNodeWithText("Demo parent").assertExists()
        compose.onNodeWithText("Back to dashboard").performClick()
        compose.onNodeWithText("Sensor").assertExists()
    }
}
