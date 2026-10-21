package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `introduction is skippable and ordinary scan works without completing it`() {
        var welcome by mutableStateOf(true)
        var scans = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, showIntroduction = welcome,
                onDismissIntroduction = { welcome = false }, onScan = { scans++ })
        } }
        compose.onNodeWithText("Welcome to PhysicalLink").assertExists()
        compose.onNodeWithText("Try Demo").assertExists()
        compose.onNodeWithText("Connect a Device").assertExists()
        compose.onNodeWithContentDescription("Scan for devices").performClick()
        assertEquals(1, scans)
        compose.onNodeWithText("Welcome to PhysicalLink").assertExists()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
        compose.onNodeWithContentDescription("Getting Started").assertExists()
        assertEquals(1, scans)
    }
    @Test fun `first run demo entry dismisses introduction without asking to scan`() {
        var demos = 0; var scans = 0; var dismissed = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, permissionDenied = true,
                showIntroduction = true, onDismissIntroduction = { dismissed++ }, onTryDemo = { demos++ }, onScan = { scans++ })
        } }
        compose.onNodeWithText("Try Demo").performScrollTo().performClick()
        assertEquals(1, dismissed); assertEquals(1, demos); assertEquals(0, scans)
    }
    @Test fun `Connect a Device delegates only an explicit scan action`() {
        var scans = 0; var dismissed = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, showIntroduction = true,
                onDismissIntroduction = { dismissed++ }, onScan = { scans++ })
        } }
        assertEquals(0, scans)
        compose.onNodeWithText("Connect a Device").performScrollTo().performClick()
        assertEquals(1, dismissed); assertEquals(1, scans)
    }
    @Test fun `guide can be opened closed and reopened and describes actual compatibility`() {
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle)
        } }
        compose.onNodeWithContentDescription("Getting Started").performClick()
        compose.onNodeWithText("Getting Started").assertExists()
        compose.onNodeWithText(gettingStartedSections[0].second).performScrollTo().assertExists()
        compose.onNodeWithText(gettingStartedSections[2].second).performScrollTo().assertExists()
        compose.onNodeWithText(gettingStartedSections[3].second).performScrollTo().assertExists()
        compose.onNodeWithText(gettingStartedSections[5].second).performScrollTo().assertExists()
        compose.onNodeWithText("Close guide").performClick()
        compose.onNodeWithText("Getting Started").assertDoesNotExist()
        compose.onNodeWithContentDescription("Getting Started").performClick()
        compose.onNodeWithText("Getting Started").assertExists()
    }
    @Test fun `permission explanation is dismissible and Continue is explicit`() {
        var visible by mutableStateOf(false)
        var proceeded = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, onScan = { visible = true })
            if (visible) PermissionExplanation(false, onDismiss = { visible = false }, onContinue = { proceeded++; visible = false })
        } }
        compose.onNodeWithText("Find your sensor project").assertDoesNotExist()
        compose.onNodeWithContentDescription("Scan for devices").performClick()
        compose.onNodeWithText("Nearby devices permission", substring = true).assertExists()
        assertEquals(0, proceeded)
        compose.onNodeWithText("Not now").performClick()
        assertEquals(0, proceeded)
        compose.onNodeWithContentDescription("Scan for devices").performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, proceeded)
    }
    @Test fun `incompatible firmware has actionable guidance and raw errors remain in details`() {
        val raw = "Invalid capability description: Unsupported capability protocol version: 99"
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Error(raw), ScanState.Idle)
        } }
        compose.onNodeWithText("Firmware compatibility issue").assertExists()
        compose.onNodeWithText("Use matching PhysicalLink firmware", substring = true).assertExists()
        compose.onNodeWithText(raw).assertDoesNotExist()
        compose.onNodeWithContentDescription("Device details").performClick()
        compose.onNodeWithText(raw).assertExists()
        compose.onNodeWithText("Connection guidance").assertExists()
    }
    @Test fun `replay introduction is temporary and does not require clearing persistent completion`() {
        var dismissals = 0
        compose.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, showIntroduction = false,
                onDismissIntroduction = { dismissals++ })
        } }
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
        compose.onNodeWithContentDescription("Getting Started").performClick()
        compose.onNodeWithText("Replay Introduction").performClick()
        compose.onNodeWithText("Getting Started").assertDoesNotExist()
        compose.onNodeWithText("Welcome to PhysicalLink").assertExists()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
        assertEquals(1, dismissals)
        compose.onNodeWithContentDescription("Getting Started").performClick()
        compose.onNodeWithText("Replay Introduction").assertExists()
    }

    @Test fun `replay state survives UI recreation but dismissed replay does not return`() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { PhysicalLinkTheme(dynamicColor = false) {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle, showIntroduction = false)
        } }
        compose.onNodeWithContentDescription("Getting Started").performClick()
        compose.onNodeWithText("Replay Introduction").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Welcome to PhysicalLink").assertExists()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
    }

}
