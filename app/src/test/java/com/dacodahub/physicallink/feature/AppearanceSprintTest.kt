package com.dacodahub.physicallink.feature

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dacodahub.physicallink.core.designsystem.theme.*
import com.dacodahub.physicallink.domain.model.*
import com.dacodahub.physicallink.feature.devices.DevicesContent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppearanceSprintTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `unknown stored appearance follows system`() {
        assertEquals(Appearance.SYSTEM, appearanceFromStorage(null))
        assertEquals(Appearance.SYSTEM, appearanceFromStorage("unknown"))
        Appearance.entries.forEach { assertEquals(it, appearanceFromStorage(it.name)) }
    }
    @Test fun `appearance survives a new settings instance`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = AppearanceSettings(context)
        try {
            Appearance.entries.forEach {
                settings.setAppearance(it)
                assertEquals(it, AppearanceSettings(context).appearance.first())
            }
        } finally { settings.setAppearance(Appearance.SYSTEM) }
    }
    @Test fun `connected home suppresses onboarding and opens dashboard`() {
        var opened = false
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(emptyList(), ConnectionState.Connected, ScanState.Idle,
                showIntroduction = true, onOpenDashboard = { opened = true })
        } }
        compose.onNodeWithText("Welcome to PhysicalLink").assertDoesNotExist()
        compose.onNodeWithText("Explore without hardware").assertDoesNotExist()
        compose.onNodeWithText("Open dashboard").performClick()
        assertTrue(opened)
        compose.onNodeWithContentDescription("Appearance").performClick()
        Appearance.entries.forEach { compose.onNodeWithText(it.label).assertExists() }
    }
}
