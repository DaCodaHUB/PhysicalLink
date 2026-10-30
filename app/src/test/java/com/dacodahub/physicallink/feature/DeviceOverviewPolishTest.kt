package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.devices.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeviceOverviewPolishTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `scan has one progress indicator one timeout explanation and one stop action while results stay selectable`() {
        var stopped = 0; var connected: String? = null
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(listOf(DiscoveredDevice("Project", "address", -62)), ConnectionState.Disconnected, ScanState.Scanning,
                onStopScan = { stopped++ }, onConnect = { connected = it })
        } }
        compose.onNodeWithTag("device-overview").performScrollToNode(hasText("Stop scanning"))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
        compose.onAllNodesWithText("Scanning stops after 15 seconds.").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Stop scanning").assertCountEquals(1)
        compose.onNodeWithContentDescription("Stop scanning").performClick()
        assertEquals(1, stopped)
        compose.onNodeWithTag("device-overview").performScrollToNode(hasText("Project"))
        compose.onNodeWithTag("device:address").performClick()
        assertEquals("address", connected)
    }
    @Test fun `connect and details are separate labeled touch targets`() {
        var connections = 0; var details = 0
        compose.setContent { PhysicalLinkTheme {
            DeviceCard(DiscoveredDevice("Project", "address", -62), { connections++ }, { details++ })
        } }
        val primary = compose.onNodeWithTag("device:address")
        val secondary = compose.onNodeWithContentDescription("Details for Project")
        secondary.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, details); assertEquals(0, connections)
        assertEquals("Connect to Project", primary.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        assertTrue(secondary.getUnclippedBoundsInRoot().left >= primary.getUnclippedBoundsInRoot().right)
        primary.performClick()
        assertEquals(1, connections); assertEquals(1, details)
    }
    @Test fun `permission guidance and settings remain readable and explicitly actionable`() {
        var opens = 0
        compose.setContent { PhysicalLinkTheme(darkTheme = true) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, permissionDenied = true, onSettings = { opens++ })
        } }
        assertEquals(0, opens)
        compose.onNodeWithTag("device-overview").performScrollToNode(hasText("Open app settings"))
        compose.onNodeWithText("Scanning needs attention").assertExists()
        compose.onNodeWithText("Allow Nearby devices permission", substring = true).assertExists()
        compose.onNodeWithText("Open app settings").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, opens)
    }
}
