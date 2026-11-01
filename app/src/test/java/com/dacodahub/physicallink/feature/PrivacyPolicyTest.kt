package com.dacodahub.physicallink.feature

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.dacodahub.physicallink.core.designsystem.theme.PhysicalLinkTheme
import com.dacodahub.physicallink.domain.model.ConnectionState
import com.dacodahub.physicallink.domain.model.ScanState
import com.dacodahub.physicallink.feature.devices.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyPolicyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Help exposes a labeled policy action and opens the exact public URL externally`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent { PhysicalLinkTheme {
            DevicesContent(emptyList(), ConnectionState.Disconnected, ScanState.Idle)
        } }
        compose.onNodeWithContentDescription("Getting Started").performClick()
        compose.onNodeWithText("Privacy Policy").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        val intent = shadowOf(app).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://dacodahub.github.io/physicallink/privacy/", intent.dataString)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(intent.component)
        assertNull(intent.extras)
        compose.onNodeWithText("Close guide").performClick()
        compose.onNodeWithText("Getting Started").assertDoesNotExist()
    }

    @Test fun `browser absence and launch restrictions fail safely`() {
        val context = mock(Context::class.java)
        doThrow(ActivityNotFoundException("No browser")).`when`(context).startActivity(any(Intent::class.java))
        assertFalse(openPrivacyPolicy(context))
        doThrow(SecurityException("Browser disabled")).`when`(context).startActivity(any(Intent::class.java))
        assertFalse(openPrivacyPolicy(context))
    }

    @Test fun `no browser shows a dismissible message and the correct address without leaving Help`() {
        compose.setContent { PhysicalLinkTheme {
            GettingStarted(onDismiss = {}, openPolicy = { false })
        } }
        compose.onNodeWithText("Privacy Policy").performClick()
        compose.onNodeWithText("Unable to open Privacy Policy").assertIsDisplayed()
        compose.onNodeWithText("https://dacodahub.github.io/physicallink/privacy/").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Unable to open Privacy Policy").assertDoesNotExist()
        compose.onNodeWithText("Getting Started").assertExists()
        compose.onNodeWithText("Privacy Policy").assertIsEnabled()
    }
}
